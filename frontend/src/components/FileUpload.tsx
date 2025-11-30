import React, { useState, FormEvent, ChangeEvent, useEffect, useRef } from 'react';
import axios from 'axios';
import './FileUpload.css';
import { UploadResponse, ProcessingErrorsResponse } from '../types/api';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

interface FileUploadProps {
  onUploadSuccess?: () => void;
}

function FileUpload({ onUploadSuccess }: FileUploadProps) {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState<boolean>(false);
  const [message, setMessage] = useState<string>('');
  const [messageType, setMessageType] = useState<'success' | 'error' | ''>('');
  const [processingErrors, setProcessingErrors] = useState<ProcessingErrorsResponse | null>(null);
  const [checkingErrors, setCheckingErrors] = useState<boolean>(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>): void => {
    const selectedFile = e.target.files?.[0];
    
    if (!selectedFile) {
      return;
    }

    if (!selectedFile.name.toLowerCase().endsWith('.csv')) {
      setMessage('Please select a CSV file');
      setMessageType('error');
      setFile(null);
      return;
    }

    setFile(selectedFile);
    setMessage('');
    setMessageType('');
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>): Promise<void> => {
    e.preventDefault();

    if (!file) {
      setMessage('Please select a file');
      setMessageType('error');
      return;
    }

    setUploading(true);
    setMessage('');
    setMessageType('');

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await axios.post<UploadResponse>(
        `${API_BASE_URL}/api/files/upload`,
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      if (response.data.success) {
        setMessage(response.data.message || 'File uploaded successfully, processing started');
        setMessageType('success');
        setFile(null);
        
        if (fileInputRef.current) {
          fileInputRef.current.value = '';
        }
        
        setTimeout(() => {
          checkProcessingErrors();
        }, 2000);
        
        if (onUploadSuccess) {
          onUploadSuccess();
        }
      } else {
        setMessage(response.data.message || 'Upload failed');
        setMessageType('error');
      }
    } catch (error) {
      let errorMessage = 'An error occurred while uploading the file';
      
      if (axios.isAxiosError(error)) {
        errorMessage = error.response?.data?.message || 
                      error.message || 
                      errorMessage;
      } else if (error instanceof Error) {
        errorMessage = error.message;
      }
      
      setMessage(errorMessage);
      setMessageType('error');
    } finally {
      setUploading(false);
    }
  };

  const checkProcessingErrors = async (): Promise<void> => {
    try {
      setCheckingErrors(true);
      const response = await axios.get<ProcessingErrorsResponse>(
        `${API_BASE_URL}/api/files/processing-errors`
      );
      
      if (response.data.hasErrors && response.data.errors.length > 0) {
        setProcessingErrors(response.data);
      } else {
        setProcessingErrors(null);
      }
    } catch (err) {
      console.error('Error fetching processing errors:', err);
    } finally {
      setCheckingErrors(false);
    }
  };

  useEffect(() => {
    if (messageType === 'success' && !checkingErrors) {
      const interval = setInterval(() => {
        checkProcessingErrors();
      }, 3000);

      const timeout = setTimeout(() => {
        clearInterval(interval);
      }, 30000);

      return () => {
        clearInterval(interval);
        clearTimeout(timeout);
      };
    }
  }, [messageType, checkingErrors]);

  return (
    <div className="card">
      <h2>Upload CSV File</h2>
      <form onSubmit={handleSubmit} className="upload-form">
        <div className="file-input-wrapper">
          <label htmlFor="file-upload" className="file-label">
            Choose CSV File
          </label>
          <input
            ref={fileInputRef}
            id="file-upload"
            type="file"
            accept=".csv"
            onChange={handleFileChange}
            disabled={uploading}
            className="file-input"
          />
          {file && (
            <div className="file-name">
              Selected: {file.name}
            </div>
          )}
        </div>

        {message && (
          <div className={`message message-${messageType}`}>
            {message}
          </div>
        )}

        {processingErrors && processingErrors.hasErrors && (
          <div className="processing-errors">
            <h3>Processing Errors</h3>
            <div className="error-summary">
              <p>
                Processed: <strong>{processingErrors.processedCount}</strong> users successfully
                {processingErrors.totalRows > 0 && (
                  <> out of <strong>{processingErrors.totalRows}</strong> total rows</>
                )}
              </p>
              <p className="error-count">
                Errors: <strong>{processingErrors.errors.length}</strong>
              </p>
            </div>
            <div className="error-list">
              <details>
                <summary>Show error details ({processingErrors.errors.length} errors)</summary>
                <ul>
                  {processingErrors.errors.map((error, index) => (
                    <li key={index} className="error-item">{error}</li>
                  ))}
                </ul>
              </details>
            </div>
          </div>
        )}

        <button
          type="submit"
          disabled={!file || uploading}
          className="upload-button"
        >
          {uploading ? 'Uploading...' : 'Upload File'}
        </button>
      </form>
    </div>
  );
}

export default FileUpload;

