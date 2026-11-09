import React, { useEffect, useState, useMemo } from 'react';
import {
  Upload,
  FileText,
  CheckCircle2,
  AlertCircle,
  Clock,
  RefreshCw,
  Search,
  Sparkles,
  Download,
  Trash2,
  Database,
  Layers,
  ChevronRight,
  Shield,
  X,
  FileCheck2,
  Sliders,
  FolderOpen
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import {
  ApiResponse,
  DocumentResponseDto,
  SearchResultDto,
  PageResponse,
  DocumentAccessScope,
  DocumentProcessingStatus
} from '@/types/api';

const CATEGORIES = ['ALL', 'POLICY', 'SOP', 'BENEFITS', 'HARDWARE', 'SECURITY', 'GENERAL'];

export const KnowledgePage: React.FC = () => {
  const [documents, setDocuments] = useState<DocumentResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [isSearchLabOpen, setIsSearchLabOpen] = useState(false);

  // Upload Form State
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadDesc, setUploadDesc] = useState('');
  const [uploadCategory, setUploadCategory] = useState('POLICY');
  const [uploadAccessScope, setUploadAccessScope] = useState<DocumentAccessScope>('ALL');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  // Semantic Search Lab State
  const [labQuery, setLabQuery] = useState('');
  const [labMinScore, setLabMinScore] = useState(0.40);
  const [labResults, setLabResults] = useState<SearchResultDto[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  // Feedback notifications
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const fetchDocuments = async () => {
    setIsLoading(true);
    setActionError(null);
    try {
      const params: Record<string, string> = { size: '100' };
      if (selectedCategory !== 'ALL') {
        params.category = selectedCategory;
      }
      const response = await apiClient.get<ApiResponse<PageResponse<DocumentResponseDto>>>('/api/v1/knowledge/documents', { params });
      if (response.data.success && response.data.data) {
        setDocuments(response.data.data.content);
      }
    } catch (err: any) {
      console.error('Failed to fetch documents', err);
      setActionError(err.response?.data?.error?.message || 'Failed to load knowledge documents');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, [selectedCategory]);

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) {
      setUploadError('Please select a file to upload');
      return;
    }
    if (!uploadTitle.trim()) {
      setUploadError('Document title is required');
      return;
    }

    setIsUploading(true);
    setUploadError(null);

    try {
      const formData = new FormData();
      formData.append('title', uploadTitle.trim());
      formData.append('description', uploadDesc.trim());
      formData.append('category', uploadCategory);
      formData.append('accessScope', uploadAccessScope);
      formData.append('file', selectedFile);

      const response = await apiClient.post<ApiResponse<DocumentResponseDto>>('/api/v1/knowledge/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      if (response.data.success) {
        setIsUploadModalOpen(false);
        resetUploadForm();
        setActionSuccess('Document uploaded, chunked, and embedded into pgvector successfully!');
        fetchDocuments();
        setTimeout(() => setActionSuccess(null), 5000);
      }
    } catch (err: any) {
      console.error('Upload failed', err);
      setUploadError(err.response?.data?.error?.message || 'Failed to upload document');
    } finally {
      setIsUploading(false);
    }
  };

  const resetUploadForm = () => {
    setUploadTitle('');
    setUploadDesc('');
    setUploadCategory('POLICY');
    setUploadAccessScope('ALL');
    setSelectedFile(null);
    setUploadError(null);
  };

  const handleDownload = async (doc: DocumentResponseDto) => {
    try {
      const response = await apiClient.get(`/api/v1/knowledge/documents/${doc.id}/versions/${doc.latestVersionNumber}/download`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', doc.latestFileName || `${doc.title}.txt`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err: any) {
      console.error('Download failed', err);
      setActionError('Failed to download document binary');
    }
  };

  const handleReindex = async (doc: DocumentResponseDto) => {
    try {
      await apiClient.post(`/api/v1/knowledge/documents/${doc.id}/reindex`);
      setActionSuccess(`Document "${doc.title}" re-indexed successfully!`);
      fetchDocuments();
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: any) {
      console.error('Reindex failed', err);
      setActionError('Failed to reindex document');
    }
  };

  const handleDelete = async (doc: DocumentResponseDto) => {
    if (!window.confirm(`Are you sure you want to delete "${doc.title}"? All pgvector chunks and storage objects will be purged.`)) {
      return;
    }
    try {
      await apiClient.delete(`/api/v1/knowledge/documents/${doc.id}`);
      setActionSuccess(`Document "${doc.title}" deleted.`);
      fetchDocuments();
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: any) {
      console.error('Delete failed', err);
      setActionError('Failed to delete document');
    }
  };

  const handleSemanticSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!labQuery.trim()) return;

    setIsSearching(true);
    setHasSearched(true);
    try {
      const response = await apiClient.post<ApiResponse<SearchResultDto[]>>('/api/v1/knowledge/search', {
        query: labQuery.trim(),
        limit: 5,
        minScore: labMinScore,
      });

      if (response.data.success && response.data.data) {
        setLabResults(response.data.data);
      }
    } catch (err: any) {
      console.error('Semantic search failed', err);
    } finally {
      setIsSearching(false);
    }
  };

  // Metrics
  const metrics = useMemo(() => {
    const totalDocs = documents.length;
    const totalChunks = documents.reduce((sum, d) => sum + (d.totalChunks || 0), 0);
    const indexedDocs = documents.filter(d => d.latestStatus === 'INDEXED').length;
    const totalBytes = documents.reduce((sum, d) => sum + (d.latestFileSizeBytes || 0), 0);
    return {
      totalDocs,
      totalChunks,
      indexedDocs,
      storageFormatted: totalBytes > 1024 * 1024 ? `${(totalBytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.round(totalBytes / 1024)} KB`
    };
  }, [documents]);

  const filteredDocuments = useMemo(() => {
    if (!searchQuery.trim()) return documents;
    const q = searchQuery.toLowerCase();
    return documents.filter(d =>
      (d.title || '').toLowerCase().includes(q) ||
      (d.description && d.description.toLowerCase().includes(q)) ||
      (d.latestFileName && d.latestFileName.toLowerCase().includes(q))
    );
  }, [documents, searchQuery]);

  const formatFileSize = (bytes: number) => {
    if (!bytes || bytes === 0) return '0 KB';
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    return `${(bytes / 1024).toFixed(1)} KB`;
  };

  const getStatusBadge = (status: DocumentProcessingStatus) => {
    switch (status) {
      case 'INDEXED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="h-3 w-3" />
            INDEXED
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <RefreshCw className="h-3 w-3 animate-spin" />
            PROCESSING
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <AlertCircle className="h-3 w-3" />
            FAILED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            <Clock className="h-3 w-3" />
            {status}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Notifications */}
      {actionSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-center justify-between shadow-sm animate-fade-in">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600 flex-shrink-0" />
            <span className="font-medium">{actionSuccess}</span>
          </div>
          <button onClick={() => setActionSuccess(null)} className="text-emerald-600 hover:text-emerald-800">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}
      {actionError && (
        <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-sm flex items-center justify-between shadow-sm animate-fade-in">
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-rose-600 flex-shrink-0" />
            <span className="font-medium">{actionError}</span>
          </div>
          <button onClick={() => setActionError(null)} className="text-rose-600 hover:text-rose-800">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Organization Knowledge Base</h1>
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
              <Sparkles className="h-3 w-3" />
              RAG Grounding
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Upload company policies, manuals, and SOPs. Chunks are embedded in pgvector for tenant-isolated Nexus AI answers.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsSearchLabOpen(true)}
            className="flex items-center gap-2 px-3.5 py-2 border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-sm font-semibold rounded-lg shadow-sm transition-colors"
          >
            <Sparkles className="h-4 w-4" />
            <span>Semantic Search Lab</span>
          </button>
          <button
            onClick={() => setIsUploadModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors"
          >
            <Upload className="h-4 w-4" />
            <span>Upload Document</span>
          </button>
        </div>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex items-center gap-3">
          <div className="p-2.5 bg-blue-50 text-blue-600 rounded-lg">
            <FileText className="h-5 w-5" />
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500">Documents</p>
            <p className="text-xl font-bold text-slate-900 mt-0.5">{metrics.totalDocs}</p>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex items-center gap-3">
          <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-lg">
            <Layers className="h-5 w-5" />
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500">Indexed Chunks</p>
            <p className="text-xl font-bold text-slate-900 mt-0.5">{metrics.totalChunks}</p>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex items-center gap-3">
          <div className="p-2.5 bg-emerald-50 text-emerald-600 rounded-lg">
            <Database className="h-5 w-5" />
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500">Vector Embeddings</p>
            <p className="text-sm font-bold text-slate-900 mt-0.5">768-dim nomic-embed</p>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex items-center gap-3">
          <div className="p-2.5 bg-purple-50 text-purple-600 rounded-lg">
            <FolderOpen className="h-5 w-5" />
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500">Storage Used</p>
            <p className="text-xl font-bold text-slate-900 mt-0.5">{metrics.storageFormatted}</p>
          </div>
        </div>
      </div>

      {/* Category Pills & Filters */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          {CATEGORIES.map(category => (
            <button
              key={category}
              onClick={() => setSelectedCategory(category)}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap ${
                selectedCategory === category
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {category}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Filter documents..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="pl-9 pr-3 py-1.5 text-sm bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 w-48 sm:w-64"
            />
          </div>
          <button
            onClick={fetchDocuments}
            title="Refresh documents"
            className="p-2 text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors"
          >
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Document Catalog Grid */}
      {isLoading ? (
        <div className="py-20 flex flex-col items-center justify-center text-slate-400">
          <RefreshCw className="h-8 w-8 animate-spin text-indigo-500 mb-2" />
          <p className="text-sm">Loading knowledge base catalog...</p>
        </div>
      ) : filteredDocuments.length === 0 ? (
        <div className="py-20 bg-white rounded-2xl border border-slate-200 flex flex-col items-center justify-center text-center px-4">
          <div className="p-3 bg-indigo-50 text-indigo-600 rounded-2xl mb-3">
            <FileText className="h-8 w-8" />
          </div>
          <h3 className="text-base font-semibold text-slate-900">No documents found</h3>
          <p className="text-sm text-slate-500 max-w-sm mt-1">
            {searchQuery
              ? 'No documents matched your filter criteria. Try a different search term.'
              : 'Your knowledge base is empty. Upload your first policy manual to enable AI assistance.'}
          </p>
          {!searchQuery && (
            <button
              onClick={() => setIsUploadModalOpen(true)}
              className="mt-4 flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors"
            >
              <Upload className="h-4 w-4" />
              <span>Upload Document</span>
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocuments.map(doc => (
            <div
              key={doc.id}
              className="bg-white rounded-xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between p-5"
            >
              <div>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700">
                      {doc.category}
                    </span>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-slate-50 text-slate-600 border border-slate-200">
                      <Shield className="h-2.5 w-2.5" />
                      {doc.accessScope}
                    </span>
                  </div>
                  {getStatusBadge(doc.latestStatus)}
                </div>

                <h3 className="text-sm font-semibold text-slate-900 line-clamp-1" title={doc.title}>
                  {doc.title}
                </h3>
                {doc.description && (
                  <p className="text-xs text-slate-500 mt-1 line-clamp-2">{doc.description}</p>
                )}

                <div className="mt-4 pt-3 border-t border-slate-100 space-y-1.5 text-xs text-slate-500">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-600 font-medium">
                      <FileCheck2 className="h-3.5 w-3.5 text-slate-400" />
                      {doc.latestFileName}
                    </span>
                    <span>{formatFileSize(doc.latestFileSizeBytes)}</span>
                  </div>
                  <div className="flex items-center justify-between text-slate-400">
                    <span>Indexed Chunks</span>
                    <span className="font-semibold text-slate-700">{doc.totalChunks} chunks</span>
                  </div>
                  <div className="flex items-center justify-between text-slate-400">
                    <span>Version</span>
                    <span>v{doc.latestVersionNumber}</span>
                  </div>
                </div>
              </div>

              <div className="mt-5 pt-3 border-t border-slate-100 flex items-center justify-between">
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => handleDownload(doc)}
                    title="Download file"
                    className="p-1.5 text-slate-600 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                  >
                    <Download className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => handleReindex(doc)}
                    title="Re-index document vectors"
                    className="p-1.5 text-slate-600 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                  >
                    <RefreshCw className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(doc)}
                    title="Delete document"
                    className="p-1.5 text-slate-600 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
                <button
                  onClick={() => {
                    setLabQuery(doc.title);
                    setIsSearchLabOpen(true);
                  }}
                  className="flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                >
                  <span>Test Search</span>
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Upload Document Modal */}
      {isUploadModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full border border-slate-200 shadow-2xl p-6 animate-scale-up">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <div className="flex items-center gap-2">
                <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
                  <Upload className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-semibold text-slate-900">Upload Knowledge Document</h3>
                  <p className="text-xs text-slate-500">Stored in MinIO S3 and chunked into pgvector</p>
                </div>
              </div>
              <button
                onClick={() => {
                  setIsUploadModalOpen(false);
                  resetUploadForm();
                }}
                className="text-slate-400 hover:text-slate-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {uploadError && (
              <div className="mb-4 p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-xs flex items-center gap-2">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                <span>{uploadError}</span>
              </div>
            )}

            <form onSubmit={handleUploadSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Document Title <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Employee Hardware &amp; Laptop Policy 2026"
                  value={uploadTitle}
                  onChange={e => setUploadTitle(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Description</label>
                <textarea
                  rows={2}
                  placeholder="Brief summary of document contents and purpose..."
                  value={uploadDesc}
                  onChange={e => setUploadDesc(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Category</label>
                  <select
                    value={uploadCategory}
                    onChange={e => setUploadCategory(e.target.value)}
                    className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  >
                    <option value="POLICY">POLICY</option>
                    <option value="SOP">SOP</option>
                    <option value="BENEFITS">BENEFITS</option>
                    <option value="HARDWARE">HARDWARE</option>
                    <option value="SECURITY">SECURITY</option>
                    <option value="GENERAL">GENERAL</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Access Scope</label>
                  <select
                    value={uploadAccessScope}
                    onChange={e => setUploadAccessScope(e.target.value as DocumentAccessScope)}
                    className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  >
                    <option value="ALL">ALL (Entire Org)</option>
                    <option value="DEPARTMENT">DEPARTMENT</option>
                    <option value="ROLE">ROLE</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  File Document <span className="text-rose-500">*</span>
                </label>
                <div className="border-2 border-dashed border-slate-200 rounded-xl p-4 text-center hover:border-indigo-400 transition-colors">
                  <input
                    type="file"
                    id="file-upload"
                    accept=".pdf,.md,.txt,.json"
                    onChange={e => {
                      if (e.target.files && e.target.files[0]) {
                        setSelectedFile(e.target.files[0]);
                        if (!uploadTitle) {
                          setUploadTitle(e.target.files[0].name.replace(/\.[^/.]+$/, ''));
                        }
                      }
                    }}
                    className="hidden"
                  />
                  <label htmlFor="file-upload" className="cursor-pointer flex flex-col items-center">
                    <FileText className="h-8 w-8 text-indigo-500 mb-1" />
                    <span className="text-xs font-semibold text-indigo-600 hover:text-indigo-800">
                      {selectedFile ? selectedFile.name : 'Choose a file or drag and drop'}
                    </span>
                    <span className="text-[11px] text-slate-400 mt-0.5">
                      PDF, Markdown, TXT, or JSON (max 25MB)
                    </span>
                    {selectedFile && (
                      <span className="text-xs text-slate-600 font-medium mt-1">
                        Size: {formatFileSize(selectedFile.size)}
                      </span>
                    )}
                  </label>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => {
                    setIsUploadModalOpen(false);
                    resetUploadForm();
                  }}
                  className="px-4 py-2 border border-slate-200 text-slate-600 hover:bg-slate-50 text-sm font-semibold rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isUploading}
                  className="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors disabled:opacity-50"
                >
                  {isUploading && <RefreshCw className="h-4 w-4 animate-spin" />}
                  <span>{isUploading ? 'Chunking & Embedding...' : 'Upload & Ingest'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Semantic Search Lab Drawer */}
      {isSearchLabOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex justify-end">
          <div className="bg-white w-full max-w-xl h-full shadow-2xl flex flex-col animate-slide-left border-l border-slate-200">
            {/* Drawer Header */}
            <div className="p-5 border-b border-slate-200 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-indigo-100 text-indigo-700 rounded-lg">
                  <Sparkles className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Semantic Search Lab</h3>
                  <p className="text-xs text-slate-500">Test pgvector cosine distance queries &amp; citations</p>
                </div>
              </div>
              <button
                onClick={() => setIsSearchLabOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Query Form */}
            <div className="p-5 border-b border-slate-100">
              <form onSubmit={handleSemanticSearch} className="space-y-3">
                <div className="relative">
                  <Search className="h-4 w-4 absolute left-3 top-3 text-slate-400" />
                  <textarea
                    rows={2}
                    value={labQuery}
                    onChange={e => setLabQuery(e.target.value)}
                    placeholder="Ask a policy question or search keywords (e.g. 'What is the developer laptop budget?')..."
                    className="w-full pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>

                <div className="flex items-center justify-between text-xs text-slate-600 pt-1">
                  <div className="flex items-center gap-2">
                    <Sliders className="h-3.5 w-3.5 text-slate-400" />
                    <span>Relevance Threshold: <strong>{Math.round(labMinScore * 100)}%</strong></span>
                  </div>
                  <input
                    type="range"
                    min="0.20"
                    max="0.80"
                    step="0.05"
                    value={labMinScore}
                    onChange={e => setLabMinScore(parseFloat(e.target.value))}
                    className="w-32 accent-indigo-600"
                  />
                  <button
                    type="submit"
                    disabled={isSearching || !labQuery.trim()}
                    className="flex items-center gap-1.5 px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg transition-colors disabled:opacity-50"
                  >
                    {isSearching ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                    <span>Query Vectors</span>
                  </button>
                </div>
              </form>
            </div>

            {/* Search Results Feed */}
            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {!hasSearched ? (
                <div className="py-20 text-center text-slate-400 flex flex-col items-center">
                  <Sparkles className="h-8 w-8 text-indigo-400 mb-2" />
                  <p className="text-sm font-semibold text-slate-700">Test Vector Similarity</p>
                  <p className="text-xs text-slate-500 max-w-xs mt-1">
                    Enter a natural language query above to retrieve top-k matching chunks with similarity scores and citations.
                  </p>
                </div>
              ) : isSearching ? (
                <div className="py-20 flex flex-col items-center justify-center text-slate-400">
                  <RefreshCw className="h-8 w-8 animate-spin text-indigo-500 mb-2" />
                  <p className="text-sm">Embedding query and searching pgvector...</p>
                </div>
              ) : labResults.length === 0 ? (
                <div className="p-6 bg-amber-50 rounded-xl border border-amber-200 text-center">
                  <AlertCircle className="h-8 w-8 text-amber-600 mx-auto mb-2" />
                  <h4 className="text-sm font-semibold text-amber-900">No Grounded Policy Found</h4>
                  <p className="text-xs text-amber-700 mt-1 max-w-sm mx-auto">
                    No document chunk scored above the {(labMinScore * 100).toFixed(0)}% similarity threshold.
                    In accordance with Nexus AI governance, the assistant will inform the user: <em>"I cannot find this in company policy."</em>
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-500">
                    <span>Retrieved {labResults.length} Grounded Citations</span>
                    <span>Top Match: {(labResults[0].similarityScore * 100).toFixed(1)}%</span>
                  </div>

                  {labResults.map((result, idx) => (
                    <div
                      key={result.chunkId || idx}
                      className="p-4 bg-white border border-slate-200 rounded-xl shadow-sm hover:border-indigo-300 transition-colors space-y-2"
                    >
                      <div className="flex items-center justify-between">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                          <CheckCircle2 className="h-3 w-3" />
                          {result.citation}
                        </span>
                        <span className="text-xs font-bold text-emerald-600">
                          {(result.similarityScore * 100).toFixed(1)}% Match
                        </span>
                      </div>

                      <p className="text-xs text-slate-800 leading-relaxed font-mono bg-slate-50 p-2.5 rounded-lg border border-slate-100 whitespace-pre-wrap">
                        {result.content}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
