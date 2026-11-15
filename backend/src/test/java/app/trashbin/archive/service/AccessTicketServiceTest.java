package app.trashbin.archive.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trashbin.archive.service.AccessTicketService.Purpose;
import app.trashbin.archive.service.AccessTicketService.Ticket;
import app.trashbin.config.ContentConfig.TicketSigningKey;
import app.trashbin.config.ContentProperties;
import app.trashbin.support.MutableClock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AccessTicketServiceTest {

    private final MutableClock clock = new MutableClock();
    private final AccessTicketService service = service("key-one-key-one-key-one-key-one!");
    private final UUID user = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();

    @Test
    void roundTripsUserFileAndPurpose() {
        Ticket ticket = service.issue(user, file, Purpose.PREVIEW);

        assertThat(service.verify(ticket.token()))
                .hasValueSatisfying(
                        v -> {
                            assertThat(v.userId()).isEqualTo(user);
                            assertThat(v.fileId()).isEqualTo(file);
                            assertThat(v.purpose()).isEqualTo(Purpose.PREVIEW);
                        });
    }

    @Test
    void downloadAndPreviewTicketsHaveTheirOwnLifetimes() {
        Ticket download = service.issue(user, file, Purpose.DOWNLOAD);
        Ticket preview = service.issue(user, file, Purpose.PREVIEW);

        clock.advance(Duration.ofSeconds(61));
        assertThat(service.verify(download.token())).isEmpty();
        assertThat(service.verify(preview.token())).isPresent();

        clock.advance(Duration.ofMinutes(5));
        assertThat(service.verify(preview.token())).isEmpty();
    }

    @Test
    void rejectsTamperedForgedAndMalformedTickets() {
        String token = service.issue(user, file, Purpose.DOWNLOAD).token();
        String payload = token.substring(0, token.indexOf('.'));
        String signature = token.substring(token.indexOf('.') + 1);

        // Swap in another file id but keep the original signature.
        String forgedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                new String(Base64.getUrlDecoder().decode(payload))
                                        .replace(file.toString(), UUID.randomUUID().toString())
                                        .getBytes());
        assertThat(service.verify(forgedPayload + "." + signature)).isEmpty();
        // Signed with a different key.
        String otherKeyToken =
                service("key-two-key-two-key-two-key-two!")
                        .issue(user, file, Purpose.DOWNLOAD)
                        .token();
        assertThat(service.verify(otherKeyToken)).isEmpty();

        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("no-dot")).isEmpty();
        assertThat(service.verify("a.b.c")).isEmpty();
        assertThat(service.verify("!!!.???")).isEmpty();
        assertThat(service.verify("x".repeat(600))).isEmpty();
    }

    private AccessTicketService service(String rawKey) {
        return new AccessTicketService(
                new TicketSigningKey(new SecretKeySpec(rawKey.getBytes(), "HmacSHA256")),
                new ContentProperties("unused", Duration.ofSeconds(60), Duration.ofMinutes(5)),
                clock);
    }
}
