import axios, { type AxiosInstance } from "axios";

/**
 * Export and import of every source of one connector as a zip holding `export.json` (D362).
 *
 * The AEM, DB and web-crawler plugins expose the same pair under their source URL:
 * `GET {sourceUrl}/export` and `POST {sourceUrl}/import` (multipart field `file`). The exported
 * `export.json` is also what the first-start import reads from `export/`.
 */
export class SourceArchiveService {
  private readonly sourceUrl: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(sourceUrl: string, axiosInstance: AxiosInstance = axios) {
    this.sourceUrl = sourceUrl;
    this.axiosInstance = axiosInstance;
  }

  async exportAll(): Promise<{ blob: Blob; filename: string | null }> {
    const response = await this.axiosInstance.get<Blob>(`${this.sourceUrl}/export`, {
      responseType: "blob",
    });
    return { blob: response.data, filename: filenameFrom(response.headers?.["content-disposition"]) };
  }

  async importArchive(file: File): Promise<void> {
    const form = new FormData();
    form.append("file", file);
    await this.axiosInstance.post(`${this.sourceUrl}/import`, form);
  }
}

/** The name the server gave the download, read from `attachment;filename=Aem_2026-11-26.zip`. */
export function filenameFrom(contentDisposition: unknown): string | null {
  if (typeof contentDisposition !== "string") return null;
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(contentDisposition);
  return match ? decodeURIComponent(match[1].trim()) : null;
}
