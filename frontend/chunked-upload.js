/**
 * Chunked file uploader for large files
 * Splits files into 50MB chunks to bypass Cloudflare's 100MB limit
 */
export class ChunkedUploader {
    constructor(options = {}) {
        this.chunkSize = options.chunkSize || 50 * 1024 * 1024; // 50MB default
        this.maxRetries = options.maxRetries || 3;
        this.retryDelay = options.retryDelay || 1000; // 1 second base delay
        this.serverElement = options.serverElement; // Vaadin server element for callbacks

        this.file = null;
        this.uploadId = null;
        this.totalChunks = 0;
        this.uploadedChunks = new Set();
        this.isUploading = false;
        this.isCancelled = false;
    }

    /**
     * Start uploading a file
     */
    async upload(file) {
        if (this.isUploading) {
            throw new Error('Upload already in progress');
        }

        this.file = file;
        this.totalChunks = Math.ceil(file.size / this.chunkSize);
        this.uploadedChunks = new Set();
        this.isUploading = true;
        this.isCancelled = false;

        try {
            // Initialize upload session
            const initResponse = await this.initializeUpload();
            this.uploadId = initResponse.uploadId;

            // Upload chunks sequentially
            for (let i = 0; i < this.totalChunks; i++) {
                if (this.isCancelled) {
                    throw new Error('Upload cancelled');
                }

                await this.uploadChunkWithRetry(i);
                this.uploadedChunks.add(i);

                // Report progress
                this.reportProgress(i + 1, this.totalChunks);
            }

            // Complete the upload
            const completeResponse = await this.completeUpload();

            if (completeResponse.status === 'COMPLETE') {
                this.reportComplete(this.uploadId);
            } else {
                throw new Error(completeResponse.error || 'Upload completion failed');
            }

        } catch (error) {
            this.reportError(error.message);
            throw error;
        } finally {
            this.isUploading = false;
        }
    }

    /**
     * Initialize the upload session
     */
    async initializeUpload() {
        const response = await fetch('/api/upload/init', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin',
            body: JSON.stringify({
                fileName: this.file.name,
                totalSize: this.file.size,
                totalChunks: this.totalChunks,
                mimeType: this.file.type
            })
        });

        if (!response.ok) {
            throw new Error(`Failed to initialize upload: ${response.status}`);
        }

        return await response.json();
    }

    /**
     * Upload a single chunk with retry logic
     */
    async uploadChunkWithRetry(chunkIndex) {
        let lastError;

        for (let attempt = 0; attempt < this.maxRetries; attempt++) {
            try {
                await this.uploadChunk(chunkIndex);
                return; // Success
            } catch (error) {
                lastError = error;
                console.warn(`Chunk ${chunkIndex} upload failed (attempt ${attempt + 1}):`, error.message);

                if (attempt < this.maxRetries - 1) {
                    // Exponential backoff
                    const delay = this.retryDelay * Math.pow(2, attempt);
                    await this.sleep(delay);
                }
            }
        }

        throw new Error(`Failed to upload chunk ${chunkIndex} after ${this.maxRetries} retries: ${lastError.message}`);
    }

    /**
     * Upload a single chunk
     */
    async uploadChunk(chunkIndex) {
        const start = chunkIndex * this.chunkSize;
        const end = Math.min(start + this.chunkSize, this.file.size);
        const chunk = this.file.slice(start, end);

        const formData = new FormData();
        formData.append('chunk', chunk);

        const response = await fetch(`/api/upload/${this.uploadId}/chunk/${chunkIndex}`, {
            method: 'POST',
            credentials: 'same-origin',
            body: formData
        });

        if (!response.ok) {
            const errorBody = await response.text();
            throw new Error(`Chunk upload failed: ${response.status} - ${errorBody}`);
        }

        const result = await response.json();
        if (!result.success) {
            throw new Error(result.error || 'Chunk upload failed');
        }
    }

    /**
     * Complete the upload
     */
    async completeUpload() {
        const response = await fetch(`/api/upload/${this.uploadId}/complete`, {
            method: 'POST',
            credentials: 'same-origin'
        });

        if (!response.ok) {
            const errorBody = await response.text();
            throw new Error(`Failed to complete upload: ${response.status} - ${errorBody}`);
        }

        return await response.json();
    }

    /**
     * Cancel the current upload
     */
    async cancel() {
        this.isCancelled = true;

        if (this.uploadId) {
            try {
                await fetch(`/api/upload/${this.uploadId}`, {
                    method: 'DELETE',
                    credentials: 'same-origin'
                });
            } catch (error) {
                console.warn('Failed to cancel upload on server:', error);
            }
        }
    }

    /**
     * Get upload status (for resume)
     */
    async getStatus(uploadId) {
        const response = await fetch(`/api/upload/${uploadId}/status`, {
            method: 'GET',
            credentials: 'same-origin'
        });

        if (!response.ok) {
            return null;
        }

        return await response.json();
    }

    /**
     * Report progress to server component
     */
    reportProgress(completed, total) {
        if (this.serverElement && this.serverElement.$server) {
            this.serverElement.$server.onChunkProgress(completed, total);
        }
    }

    /**
     * Report upload complete to server component
     */
    reportComplete(uploadId) {
        if (this.serverElement && this.serverElement.$server) {
            this.serverElement.$server.onUploadComplete(uploadId);
        }
    }

    /**
     * Report error to server component
     */
    reportError(error) {
        if (this.serverElement && this.serverElement.$server) {
            this.serverElement.$server.onUploadError(error);
        }
    }

    /**
     * Sleep helper
     */
    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// Make available globally for Vaadin integration
window.ChunkedUploader = ChunkedUploader;
