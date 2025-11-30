export interface UploadResponse {
  success: boolean;
  message: string;
  fileName: string | null;
}

export interface UserCountResponse {
  count: number;
}

export interface ProcessingErrorsResponse {
  hasErrors: boolean;
  errors: string[];
  processedCount: number;
  totalRows: number;
  fileName: string | null;
}

