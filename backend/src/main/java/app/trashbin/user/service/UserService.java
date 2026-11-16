package app.trashbin.user.service;

import app.trashbin.common.error.EmailAlreadyRegisteredException;
import app.trashbin.common.error.InvalidCredentialsException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.config.AuthProperties;
import app.trashbin.user.domain.User;
import app.trashbin.user.domain.UserRole;
import app.trashbin.user.dto.UserProfile;
import app.trashbin.user.mapper.UserMapper;
import app.trashbin.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts: creation, credential checks with lockout, profile and password changes. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    /** Compared against when the email is unknown so both paths cost one hash (timing). */
    private final String dummyHash;

    public UserService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserProfile register(String email, String displayName, String password) {
        PasswordPolicy.validate(password);
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        UserRole role =
                normalizedEmail.equals(normalizeEmail(properties.bootstrapAdminEmail()))
                        ? UserRole.ADMIN
                        : UserRole.USER;
        User user =
                new User(
                        normalizedEmail,
                        displayName.trim(),
                        passwordEncoder.encode(password),
                        role,
                        Instant.now(clock));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same email.
            throw new EmailAlreadyRegisteredException();
        }
        log.info("Registered user {} with role {}", user.getId(), role);
        return UserMapper.toProfile(user);
    }

    /**
     * Verifies credentials. Every failure throws the same {@link InvalidCredentialsException}; the
     * failed-attempt counter must still be saved, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public UserProfile authenticate(String email, String password) {
        Instant now = Instant.now(clock);
        Optional<User> found = users.findByEmail(normalizeEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        if (!user.canSignIn(now)) {
            log.warn("Sign-in rejected for locked or disabled user {}", user.getId());
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches) {
            user.recordFailedLogin(now, properties.maxFailedLogins(), properties.lockDuration());
            log.warn("Failed sign-in for user {}", user.getId());
            throw new InvalidCredentialsException();
        }
        user.recordSuccessfulLogin(now);
        return UserMapper.toProfile(user);
    }

    /** Profile of an account that may still sign in; empty when missing, locked or disabled. */
    @Transactional(readOnly = true)
    public Optional<UserProfile> findActiveProfile(UUID userId) {
        Instant now = Instant.now(clock);
        return users.findById(userId).filter(u -> u.canSignIn(now)).map(UserMapper::toProfile);
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(AuthenticatedUser caller) {
        return UserMapper.toProfile(require(caller));
    }

    @Transactional
    public UserProfile updateProfile(AuthenticatedUser caller, String displayName) {
        User user = require(caller);
        user.rename(displayName.trim(), Instant.now(clock));
        return UserMapper.toProfile(user);
    }

    @Transactional
    public void changePassword(AuthenticatedUser caller, String current, String replacement) {
        User user = require(caller);
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.validate(replacement);
        user.changePassword(passwordEncoder.encode(replacement), Instant.now(clock));
        log.info("Password changed for user {}", user.getId());
    }

    /**
     * Sets a new password for the account with {@code email} without knowing the old one, and lifts
     * any lockout. Only reachable from the operator recovery command (ADR-003), never HTTP.
     *
     * @return the account id
     */
    @Transactional
    public UUID resetPassword(String email, String replacement) {
        PasswordPolicy.validate(replacement);
        User user =
                users.findByEmail(normalizeEmail(email))
                        .orElseThrow(ResourceNotFoundException::new);
        user.resetPassword(passwordEncoder.encode(replacement), Instant.now(clock));
        log.warn("Password reset by operator for user {}", user.getId());
        return user.getId();
    }

    /**
     * Takes a row lock on the account for the rest of the caller's transaction. Used to serialize
     * one user's quota-affecting writes.
     */
    @Transactional
    public void lockAccount(UUID userId) {
        users.findByIdForUpdate(userId).orElseThrow(ResourceNotFoundException::new);
    }

    private User require(AuthenticatedUser caller) {
        return users.findById(caller.id()).orElseThrow(ResourceNotFoundException::new);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
