package app.trashbin.archive.dto;

import org.springframework.core.io.InputStreamSource;

/**
 * An uploaded file as the service layer sees it, independent of the web framework. Every value
 * except the bytes is client-supplied and treated as a hint only.
 *
 * @param declaredSize size claimed by the client; checked early, then enforced on the stream
 */
public record IncomingFile(
        String originalName, String declaredType, long declaredSize, InputStreamSource content) {}
