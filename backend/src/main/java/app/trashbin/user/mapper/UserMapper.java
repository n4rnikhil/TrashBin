package app.trashbin.user.mapper;

import app.trashbin.user.domain.User;
import app.trashbin.user.dto.UserProfile;

public final class UserMapper {

    private UserMapper() {}

    public static UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
