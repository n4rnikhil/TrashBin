/**
 * Starts a browser download from a same-origin URL. The server marks the response as an
 * attachment, so the current page stays where it is.
 */
export function startDownload(url: string): void {
  const link = document.createElement("a");
  link.href = url;
  link.rel = "noopener";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  link.remove();
}
