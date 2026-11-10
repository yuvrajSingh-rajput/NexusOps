import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  Send,
  Loader2,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FileText,
  Clock,
  ArrowRight,
  ShieldCheck,
  Bot,
  User,
  Plus,
  MessageSquare,
  Cpu,
  Lock,
  X,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { MarkdownRenderer } from '@/components/common/MarkdownRenderer';
import {
  ApiResponse,
  ChatResponseDto,
  ChatMessageDto,
  ChatCitationDto,
  ActionProposalDto,
  RequestResponseDto,
  ConversationSummaryDto,
  PageResponse
} from '@/types/api';
import { SEO } from '@/components/common/SEO';

export const AiAssistantPage: React.FC = () => {
  const [conversations, setConversations] = useState<ConversationSummaryDto[]>([]);
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [selectedCitation, setSelectedCitation] = useState<ChatCitationDto | null>(null);
  const [actionConfirmingId, setActionConfirmingId] = useState<string | null>(null);
  const [confirmedRequests, setConfirmedRequests] = useState<Record<string, RequestResponseDto>>({});
  const [cancelledActions, setCancelledActions] = useState<Record<string, boolean>>({});
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({});
  const [mobileChatsOpen, setMobileChatsOpen] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Fetch conversations list on mount
  useEffect(() => {
    loadConversations();
  }, []);

  // Fetch messages when activeConvId changes
  useEffect(() => {
    if (activeConvId) {
      loadConversationMessages(activeConvId);
    } else {
      setMessages([]);
    }
  }, [activeConvId]);

  const scrollToBottom = () => {
    requestAnimationFrame(() => {
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
      }, 50);
    });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isSending]);

  const loadConversations = async () => {
    try {
      const res = (await apiClient.get('/ai/conversations')) as unknown as ApiResponse<
        PageResponse<ConversationSummaryDto>
      >;
      if (res && res.data && res.data.content) {
        setConversations(res.data.content);
      }
    } catch (err) {
      // Fallback empty
      setConversations([]);
    }
  };

  const loadConversationMessages = async (id: string) => {
    setIsLoadingHistory(true);
    try {
      const res = (await apiClient.get(`/ai/conversations/${id}`)) as unknown as ApiResponse<ChatMessageDto[]>;
      if (res && res.data) {
        setMessages(res.data);
      }
    } catch (err) {
      setMessages([]);
    } finally {
      setIsLoadingHistory(false);
    }
  };

  const handleStartNewChat = () => {
    setActiveConvId(null);
    setMessages([]);
    setInputMessage('');
  };

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputMessage).trim();
    if (!text || isSending) return;

    const userMsg: ChatMessageDto = {
      id: crypto.randomUUID(),
      role: 'USER',
      content: text,
      citations: [],
      createdAt: new Date().toISOString()
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputMessage('');
    setIsSending(true);

    try {
      const res = (await apiClient.post('/ai/chat', {
        conversationId: activeConvId,
        message: text
      })) as unknown as ApiResponse<ChatResponseDto>;

      if (res && res.data) {
        if (!activeConvId) {
          setActiveConvId(res.data.conversationId);
          loadConversations();
        }
        const botMsg: ChatMessageDto = {
          id: res.data.messageId,
          role: 'ASSISTANT',
          content: res.data.reply,
          citations: res.data.citations || [],
          proposal: res.data.proposal || null,
          createdAt: new Date().toISOString()
        };
        setMessages((prev) => [...prev, botMsg]);

        // If an action proposal was confirmed via chat
        if (res.data.proposal && (res.data.toolUsed === 'confirmAction' || res.data.proposal.status === 'SUCCEEDED')) {
          const prop = res.data.proposal;
          const reqNum = prop.parameters?.requestNumber ? String(prop.parameters.requestNumber) : 'Created';
          setConfirmedRequests((prev) => ({
            ...prev,
            [prop.actionId]: {
              id: prop.actionId,
              requestNumber: reqNum,
              title: prop.title,
              status: 'IN_REVIEW' as any
            } as any
          }));

          // Also update any prior proposal in the conversation so older cards switch to confirmed
          setMessages((prev) =>
            prev.map((m) =>
              m.proposal
                ? {
                    ...m,
                    proposal: {
                      ...m.proposal,
                      status: 'SUCCEEDED' as any,
                      parameters: {
                        ...m.proposal.parameters,
                        requestNumber: reqNum
                      }
                    }
                  }
                : m
            )
          );
        }
      }
    } catch (err: any) {
      const errorMsg: ChatMessageDto = {
        id: crypto.randomUUID(),
        role: 'ASSISTANT',
        content: `⚠️ Error communicating with Nexus AI service: ${err.message || 'Unknown network error'}.`,
        citations: [],
        createdAt: new Date().toISOString()
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsSending(false);
    }
  };

  const handleConfirmAction = async (proposal: ActionProposalDto) => {
    setActionConfirmingId(proposal.actionId);
    setActionErrors((prev) => {
      const next = { ...prev };
      delete next[proposal.actionId];
      return next;
    });
    try {
      const res = (await apiClient.post(`/ai/actions/${proposal.actionId}/confirm`, {
        confirm: true,
        notes: 'Confirmed via Nexus AI Studio'
      })) as unknown as ApiResponse<RequestResponseDto>;

      if (res && res.data) {
        const confirmedData = res.data;
        setConfirmedRequests((prev) => ({
          ...prev,
          [proposal.actionId]: confirmedData
        }));

        // Add assistant confirmation message to conversation
        setMessages((prev) => [
          ...prev,
          {
            id: crypto.randomUUID(),
            role: 'ASSISTANT',
            content: `Action Confirmed! I have submitted your request **${confirmedData.requestNumber}** ("${confirmedData.title}").\n\nIt has entered the active approval workflow and reviewers/operators have been notified. You can track its progress in [My Requests](/requests).`,
            citations: [],
            createdAt: new Date().toISOString()
          }
        ]);
      }
    } catch (err: any) {
      const msg = err?.message || err?.error?.message || 'Backend validation rejected the request.';
      setActionErrors((prev) => ({ ...prev, [proposal.actionId]: msg }));
    } finally {
      setActionConfirmingId(null);
    }
  };

  const handleCancelAction = async (proposal: ActionProposalDto) => {
    try {
      await apiClient.post(`/ai/actions/${proposal.actionId}/cancel`);
      setCancelledActions((prev) => ({ ...prev, [proposal.actionId]: true }));
    } catch (err) {
      setCancelledActions((prev) => ({ ...prev, [proposal.actionId]: true }));
    }
  };

  return (
    <div className="flex h-[calc(100vh-4rem)] sm:h-[calc(100vh-5rem)] md:h-[calc(100vh-6rem)] -m-3 sm:-m-4 md:-m-6 bg-slate-50 border-t border-slate-200 overflow-hidden relative">
      <SEO title="Nexus AI Co-pilot" noIndex={true} />
      {/* 1. Left Sidebar: Conversation History (Desktop) */}
      <div className="hidden md:flex w-72 bg-white border-r border-slate-200 flex-col shrink-0">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-indigo-600 rounded-lg text-white">
              <Sparkles className="h-4 w-4" />
            </div>
            <h2 className="text-sm font-bold text-slate-900">Nexus AI Chats</h2>
          </div>
          <button
            onClick={handleStartNewChat}
            className="p-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-lg text-xs font-semibold flex items-center gap-1 transition-colors"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>New</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          {conversations.length === 0 ? (
            <div className="text-center py-8 text-xs text-slate-400">
              <MessageSquare className="h-8 w-8 mx-auto mb-2 text-slate-300" />
              <p>No previous conversations.</p>
              <p className="text-[11px] mt-1">Start a new query to get started.</p>
            </div>
          ) : (
            conversations.map((c) => (
              <button
                key={c.id}
                onClick={() => setActiveConvId(c.id)}
                className={`w-full text-left p-3 rounded-lg text-xs transition-colors flex items-center justify-between ${
                  activeConvId === c.id
                    ? 'bg-indigo-50/80 text-indigo-900 border border-indigo-200 font-medium'
                    : 'hover:bg-slate-50 text-slate-700 border border-transparent'
                }`}
              >
                <div className="truncate pr-2">
                  <p className="truncate font-medium">{c.title || 'Conversation'}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5">
                    {new Date(c.createdAt).toLocaleDateString()} · {c.messageCount} messages
                  </p>
                </div>
              </button>
            ))
          )}
        </div>
      </div>

      {/* 1b. Mobile Conversation History Drawer */}
      {mobileChatsOpen && (
        <>
          <div
            className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs z-40 md:hidden animate-in fade-in duration-150"
            onClick={() => setMobileChatsOpen(false)}
          />
          <div className="fixed inset-y-0 left-0 w-72 max-w-[85vw] bg-white border-r border-slate-200 flex flex-col z-50 shadow-2xl md:hidden animate-in slide-in-from-left duration-200">
            <div className="p-4 border-b border-slate-200 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-indigo-600 rounded-lg text-white">
                  <Sparkles className="h-4 w-4" />
                </div>
                <h2 className="text-sm font-bold text-slate-900">Nexus AI Chats</h2>
              </div>
              <button
                onClick={() => setMobileChatsOpen(false)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-1">
              {conversations.length === 0 ? (
                <div className="text-center py-8 text-xs text-slate-400">
                  <MessageSquare className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                  <p>No previous conversations.</p>
                </div>
              ) : (
                conversations.map((c) => (
                  <button
                    key={c.id}
                    onClick={() => {
                      setActiveConvId(c.id);
                      setMobileChatsOpen(false);
                    }}
                    className={`w-full text-left p-3 rounded-lg text-xs transition-colors flex items-center justify-between ${
                      activeConvId === c.id
                        ? 'bg-indigo-50/80 text-indigo-900 border border-indigo-200 font-medium'
                        : 'hover:bg-slate-50 text-slate-700 border border-transparent'
                    }`}
                  >
                    <div className="truncate pr-2">
                      <p className="truncate font-medium">{c.title || 'Conversation'}</p>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        {new Date(c.createdAt).toLocaleDateString()} · {c.messageCount} messages
                      </p>
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>
        </>
      )}

      {/* 2. Center: Chat Canvas */}
      <div className="flex-1 flex flex-col min-w-0 bg-slate-50/50">
        {/* Mobile Subheader Bar */}
        <div className="md:hidden flex items-center justify-between px-3 py-2 border-b border-slate-200 bg-white shrink-0">
          <button
            type="button"
            onClick={() => setMobileChatsOpen(true)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
          >
            <MessageSquare className="h-3.5 w-3.5 text-indigo-600" />
            <span>Chats ({conversations.length})</span>
          </button>
          <button
            type="button"
            onClick={handleStartNewChat}
            className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 rounded-lg transition-colors"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>New Chat</span>
          </button>
        </div>

        {/* Messages Stream */}
        <div className="flex-1 overflow-y-auto p-3 sm:p-6 pb-28 sm:pb-36 space-y-4">
          {messages.length === 0 && !isLoadingHistory && (
            <div className="max-w-2xl mx-auto my-4 sm:my-8 space-y-6">
              <div className="text-center space-y-2">
                <div className="inline-flex p-3 bg-indigo-600 rounded-2xl text-white shadow-lg shadow-indigo-500/20 mb-2">
                  <Bot className="h-8 w-8" />
                </div>
                <h1 className="text-xl sm:text-2xl font-bold text-slate-900">Nexus AI Operations Co-pilot</h1>
                <p className="text-xs sm:text-sm text-slate-500 max-w-md mx-auto">
                  Grounded enterprise assistant powered by company documents, strict tenant isolation, and governed two-phase tool execution.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                <button
                  onClick={() => handleSendMessage('What is our developer laptop budget and refresh policy?')}
                  className="p-4 bg-white hover:bg-indigo-50/40 border border-slate-200 hover:border-indigo-200 rounded-xl text-left transition-all shadow-xs group"
                >
                  <div className="flex items-center justify-between text-indigo-600 mb-1">
                    <span className="text-xs font-semibold">Policy Inquiries</span>
                    <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
                  </div>
                  <p className="text-xs text-slate-700 font-medium">"What is our developer laptop budget and refresh cycle?"</p>
                  <p className="text-[11px] text-slate-400 mt-1">Grounds answers directly from uploaded policy handbooks.</p>
                </button>

                <button
                  onClick={() => handleSendMessage('Show my submitted requests')}
                  className="p-4 bg-white hover:bg-indigo-50/40 border border-slate-200 hover:border-indigo-200 rounded-xl text-left transition-all shadow-xs group"
                >
                  <div className="flex items-center justify-between text-indigo-600 mb-1">
                    <span className="text-xs font-semibold">Track Operations</span>
                    <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
                  </div>
                  <p className="text-xs text-slate-700 font-medium">"Show my active submitted requests"</p>
                  <p className="text-[11px] text-slate-400 mt-1">Queries your personal service intake tickets securely.</p>
                </button>

                <button
                  onClick={() => handleSendMessage('Show my pending approvals')}
                  className="p-4 bg-white hover:bg-indigo-50/40 border border-slate-200 hover:border-indigo-200 rounded-xl text-left transition-all shadow-xs group"
                >
                  <div className="flex items-center justify-between text-indigo-600 mb-1">
                    <span className="text-xs font-semibold">Approvals Desk</span>
                    <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
                  </div>
                  <p className="text-xs text-slate-700 font-medium">"Show my pending approvals"</p>
                  <p className="text-[11px] text-slate-400 mt-1">Lists queue items awaiting your authorization decision.</p>
                </button>

                <button
                  onClick={() => handleSendMessage('I need a high performance developer laptop')}
                  className="p-4 bg-white hover:bg-indigo-50/40 border border-slate-200 hover:border-indigo-200 rounded-xl text-left transition-all shadow-xs group"
                >
                  <div className="flex items-center justify-between text-amber-600 mb-1">
                    <span className="text-xs font-semibold">Two-Phase Action</span>
                    <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-amber-600 transition-colors" />
                  </div>
                  <p className="text-xs text-slate-700 font-medium">"I need a high performance developer laptop"</p>
                  <p className="text-[11px] text-slate-400 mt-1">Drafts an actionable proposal card awaiting your review.</p>
                </button>
              </div>
            </div>
          )}

          {isLoadingHistory && (
            <div className="flex items-center justify-center py-12 text-xs text-slate-400 gap-2">
              <Loader2 className="h-4 w-4 animate-spin text-indigo-600" />
              <span>Loading conversation history...</span>
            </div>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex flex-col ${msg.role === 'USER' ? 'items-end' : 'items-start'} max-w-3xl mx-auto space-y-1.5`}
            >
              <div className="flex items-center gap-2 px-1 text-[11px] text-slate-400">
                {msg.role === 'USER' ? (
                  <>
                    <span className="font-medium">You</span>
                    <User className="h-3.5 w-3.5" />
                  </>
                ) : (
                  <>
                    <Bot className="h-3.5 w-3.5 text-indigo-600" />
                    <span className="font-medium text-slate-700">Nexus AI Assistant</span>
                  </>
                )}
              </div>

              <div
                className={`rounded-2xl p-4 text-xs shadow-xs leading-relaxed ${
                  msg.role === 'USER'
                    ? 'bg-indigo-600 text-white rounded-br-none max-w-xl whitespace-pre-wrap'
                    : 'bg-white text-slate-800 border border-slate-200/90 rounded-bl-none w-full'
                }`}
              >
                {msg.role === 'USER' ? (
                  msg.content
                ) : (
                  <MarkdownRenderer content={msg.content} />
                )}

                {/* Grounded Citations */}
                {msg.citations && msg.citations.length > 0 && (
                  <div className="mt-4 pt-3 border-t border-slate-100 space-y-2">
                    <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      Grounded Policy Citations:
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {msg.citations.map((c, idx) => (
                        <button
                          key={idx}
                          onClick={() => setSelectedCitation(c)}
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-lg border border-indigo-200 font-medium transition-colors"
                        >
                          <FileText className="h-3.5 w-3.5 text-indigo-500" />
                          <span>{c.documentTitle}</span>
                          {c.pageNumber && <span className="text-indigo-400">(p. {c.pageNumber})</span>}
                          <span className="text-[10px] text-indigo-400 bg-white/60 px-1 rounded">
                            {(c.similarityScore * 100).toFixed(0)}%
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Action Proposal Card */}
              {msg.proposal && (() => {
                const isConfirmed = !!confirmedRequests[msg.proposal.actionId] || msg.proposal.status === 'SUCCEEDED';
                const confirmedNumber = confirmedRequests[msg.proposal.actionId]?.requestNumber || msg.proposal.parameters?.requestNumber;
                const isCancelled = !!cancelledActions[msg.proposal.actionId] || msg.proposal.status === 'REJECTED';

                return (
                  <div className={`w-full rounded-2xl p-4 shadow-xs space-y-3 mt-1 border transition-all ${
                    isConfirmed
                      ? 'bg-emerald-50/50 border-emerald-300'
                      : isCancelled
                      ? 'bg-slate-50 border-slate-200'
                      : 'bg-white border-amber-300 shadow-sm'
                  }`}>
                    <div className="flex items-start justify-between gap-2 border-b border-amber-100/80 pb-3">
                      <div className="flex items-center gap-2.5">
                        <div className={`p-1.5 rounded-lg ${isConfirmed ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                          {isConfirmed ? <CheckCircle2 className="h-5 w-5" /> : <AlertTriangle className="h-5 w-5" />}
                        </div>
                        <div>
                          <h4 className="text-sm font-bold text-slate-900">
                            {msg.proposal.title || 'Proposed Request'}
                          </h4>
                          <p className="text-xs text-slate-500 font-mono">Governed Tool: {msg.proposal.toolName}</p>
                        </div>
                      </div>
                      <span className={`px-2.5 py-1 text-xs font-semibold rounded-full border ${
                        isConfirmed
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : isCancelled
                          ? 'bg-slate-100 text-slate-600 border-slate-200'
                          : 'bg-amber-50 text-amber-800 border-amber-200 animate-pulse'
                      }`}>
                        {isConfirmed ? 'Action Executed' : isCancelled ? 'Dismissed' : 'Awaiting Confirmation'}
                      </span>
                    </div>

                    <p className="text-xs text-slate-600">{msg.proposal.description}</p>

                    {/* Parameters Grid */}
                    {msg.proposal.parameters && (
                      <div className="bg-slate-50/90 border border-slate-200 rounded-xl p-3 text-xs space-y-1.5">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                          {Object.entries(msg.proposal.parameters)
                            .filter(([k]) => k !== 'title' && k !== 'categoryCode')
                            .map(([k, v]) => (
                              <div key={k} className="flex justify-between items-center py-0.5 border-b border-slate-100 last:border-b-0">
                                <span className="text-slate-500 capitalize">{k.replace(/([A-Z])/g, ' $1')}:</span>
                                <span className="font-semibold text-slate-800 font-mono text-[11px] truncate max-w-[180px] sm:max-w-[220px]" title={String(v)}>
                                  {k.toLowerCase().includes('amount') ? `$${Number(v).toFixed(2)}` : String(v)}
                                </span>
                              </div>
                            ))}
                        </div>
                      </div>
                    )}

                    {!isConfirmed && !isCancelled && (
                      <div className="flex items-center gap-1.5 text-xs text-slate-500">
                        <Clock className="h-3.5 w-3.5 text-amber-600" />
                        <span>Durable TTL: Expires in 15 minutes unless confirmed.</span>
                      </div>
                    )}

                    {/* Inline Error Notice */}
                    {actionErrors[msg.proposal.actionId] && (
                      <div className="bg-rose-50 border border-rose-200 rounded-lg p-3 flex items-start gap-2.5 text-xs text-rose-800">
                        <AlertTriangle className="h-4 w-4 text-rose-600 shrink-0 mt-0.5" />
                        <span>{actionErrors[msg.proposal.actionId]}</span>
                      </div>
                    )}

                    {isConfirmed ? (
                      <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-3.5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs text-emerald-800">
                        <div className="flex items-center gap-3">
                          <CheckCircle2 className="h-6 w-6 text-emerald-600 shrink-0" />
                          <div>
                            <span className="font-bold text-sm text-emerald-950">Successfully Created in Database!</span>
                            <p className="text-xs text-emerald-700 mt-0.5">
                              Request Number: <strong className="font-mono font-bold text-emerald-900">{confirmedNumber || 'Created'}</strong> · Workflow Active
                            </p>
                          </div>
                        </div>
                        <Link
                          to="/requests"
                          className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg font-semibold text-xs transition-colors shadow-xs shrink-0"
                        >
                          <span>View in My Requests</span>
                          <ArrowRight className="h-3.5 w-3.5" />
                        </Link>
                      </div>
                    ) : isCancelled ? (
                      <div className="bg-slate-100 border border-slate-200 rounded-xl p-3 flex items-center gap-2 text-xs text-slate-600">
                        <XCircle className="h-4 w-4 text-slate-400 shrink-0" />
                        <span>Action proposal was dismissed.</span>
                      </div>
                    ) : (
                      <div className="space-y-2 pt-1">
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            onClick={() => handleConfirmAction(msg.proposal!)}
                            disabled={actionConfirmingId === msg.proposal.actionId}
                            className="flex-1 py-2.5 px-4 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-xl text-xs shadow-md hover:shadow-lg flex items-center justify-center gap-2 transition-all disabled:opacity-50 cursor-pointer"
                          >
                            {actionConfirmingId === msg.proposal.actionId ? (
                              <>
                                <Loader2 className="h-4 w-4 animate-spin" />
                                <span>Validating &amp; Creating via Backend Service...</span>
                              </>
                            ) : (
                              <>
                                <CheckCircle2 className="h-4 w-4" />
                                <span>Confirm &amp; Execute Action</span>
                              </>
                            )}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleCancelAction(msg.proposal!)}
                            disabled={actionConfirmingId === msg.proposal.actionId}
                            className="py-2.5 px-4 border border-slate-300 hover:bg-slate-100 text-slate-700 rounded-xl text-xs font-semibold transition-colors cursor-pointer"
                          >
                            Dismiss
                          </button>
                        </div>
                        <p className="text-[11px] text-slate-400 text-center">
                          💡 You can also reply <span className="font-semibold text-slate-600">"can you make it work"</span>, <span className="font-semibold text-slate-600">"yes"</span>, or <span className="font-semibold text-slate-600">"confirm"</span> in chat!
                        </p>
                      </div>
                    )}
                  </div>
                );
              })()}
            </div>
          ))}

          {isSending && (
            <div className="max-w-3xl mx-auto flex items-center gap-2 text-xs text-slate-400 p-2">
              <Loader2 className="h-4 w-4 animate-spin text-indigo-600" />
              <span>Nexus AI is formulating response &amp; evaluating tool permissions...</span>
            </div>
          )}

          <div ref={messagesEndRef} className="h-10 shrink-0" />
        </div>

        {/* Quick Suggestion Chips */}
        <div className="px-4 py-2 border-t border-slate-100 bg-slate-50/60 flex items-center gap-2 overflow-x-auto text-[11px]">
          <span className="text-slate-400 font-medium shrink-0 flex items-center gap-1">
            <Sparkles className="h-3 w-3 text-indigo-500" />
            Try:
          </span>
          <button
            type="button"
            onClick={() => handleSendMessage('Create an expense reimbursement request')}
            className="shrink-0 px-2.5 py-1 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
          >
            "Create an expense reimbursement request"
          </button>
          <button
            type="button"
            onClick={() => handleSendMessage('I need a high performance developer laptop')}
            className="shrink-0 px-2.5 py-1 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
          >
            "I need a MacBook Pro 16"
          </button>
          <button
            type="button"
            onClick={() => handleSendMessage('Request access to GitHub Copilot Enterprise')}
            className="shrink-0 px-2.5 py-1 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
          >
            "Request GitHub Copilot license"
          </button>
          <button
            type="button"
            onClick={() => handleSendMessage('What is our expense reimbursement policy?')}
            className="shrink-0 px-2.5 py-1 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
          >
            "What is our reimbursement policy?"
          </button>
        </div>

        {/* Input Bar */}
        <div className="p-4 border-t border-slate-200 bg-white">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSendMessage();
            }}
            className="max-w-3xl mx-auto flex items-center gap-2"
          >
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              disabled={isSending}
              placeholder="Ask a policy question or request an action..."
              className="flex-1 px-4 py-3 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-50"
            />
            <button
              type="submit"
              disabled={isSending || !inputMessage.trim()}
              className="p-3 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl shadow-sm transition-colors"
            >
              <Send className="h-4 w-4" />
            </button>
          </form>
        </div>
      </div>

      {/* 3. Right Sidebar: Governance & Architecture Inspector */}
      <div className="w-80 bg-white border-l border-slate-200 p-5 overflow-y-auto hidden lg:flex flex-col space-y-6 shrink-0">
        <div>
          <div className="flex items-center gap-2 text-indigo-700 font-bold text-xs uppercase tracking-wider mb-2">
            <ShieldCheck className="h-4 w-4 text-emerald-600" />
            <span>AI Governance &amp; Security</span>
          </div>
          <p className="text-xs text-slate-500 leading-relaxed">
            Nexus AI adheres strictly to the rule: <em>"The LLM proposes; Java services validate, authorize and execute."</em>
          </p>
        </div>

        {/* Principles */}
        <div className="space-y-3">
          <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-3.5 space-y-1.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-800">
              <Lock className="h-3.5 w-3.5 text-indigo-600" />
              <span>Two-Phase Action Registry</span>
            </div>
            <p className="text-[11px] text-slate-500 leading-normal">
              Write actions return a draft proposal card. No repository or database mutation executes until explicitly confirmed by your authenticated session.
            </p>
          </div>

          <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-3.5 space-y-1.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-800">
              <Cpu className="h-3.5 w-3.5 text-emerald-600" />
              <span>Prompt Injection Defense</span>
            </div>
            <p className="text-[11px] text-slate-500 leading-normal">
              Knowledge excerpts are wrapped in strict untrusted XML boundary delimiters. The system treats retrieved documents as context, never instructions.
            </p>
          </div>

          <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-3.5 space-y-1.5">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-800">
              <Bot className="h-3.5 w-3.5 text-amber-600" />
              <span>Zero-Cloud Degradation</span>
            </div>
            <p className="text-[11px] text-slate-500 leading-normal">
              If Ollama or external LLMs are unavailable, Nexus AI automatically falls back to deterministic rule routing and template synthesis.
            </p>
          </div>
        </div>

        {/* Registered Governed Tools */}
        <div className="space-y-2 pt-2 border-t border-slate-100">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            Registered Governed Tools
          </span>
          <div className="space-y-1.5 text-xs text-slate-600">
            <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200/60 font-mono text-[11px]">
              <span>searchMyRequests</span>
              <span className="text-emerald-600 font-sans text-[10px] font-semibold">READ</span>
            </div>
            <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200/60 font-mono text-[11px]">
              <span>searchPendingApprovals</span>
              <span className="text-emerald-600 font-sans text-[10px] font-semibold">READ</span>
            </div>
            <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200/60 font-mono text-[11px]">
              <span>searchMyTasks</span>
              <span className="text-emerald-600 font-sans text-[10px] font-semibold">READ</span>
            </div>
            <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200/60 font-mono text-[11px]">
              <span>searchKnowledgeBase</span>
              <span className="text-emerald-600 font-sans text-[10px] font-semibold">RAG</span>
            </div>
            <div className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200/60 font-mono text-[11px]">
              <span>createRequest</span>
              <span className="text-amber-600 font-sans text-[10px] font-semibold">PROPOSAL</span>
            </div>
          </div>
        </div>
      </div>

      {/* Citation Popover Modal */}
      {selectedCitation && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full border border-slate-200 p-5 space-y-3 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
              <div className="flex items-center gap-2">
                <FileText className="h-4 w-4 text-indigo-600" />
                <h4 className="text-sm font-semibold text-slate-900">{selectedCitation.documentTitle}</h4>
              </div>
              <button
                onClick={() => setSelectedCitation(null)}
                className="p-1 text-slate-400 hover:text-slate-600 rounded-md"
              >
                <XCircle className="h-4 w-4" />
              </button>
            </div>

            <div className="text-[11px] text-slate-400 flex items-center gap-3">
              {selectedCitation.pageNumber && <span>Page: {selectedCitation.pageNumber}</span>}
              <span>
                Semantic Match: {(selectedCitation.similarityScore * 100).toFixed(1)}%
              </span>
            </div>

            <div className="bg-slate-50 border border-slate-200/80 rounded-lg p-3 text-xs text-slate-700 leading-relaxed font-mono">
              "{selectedCitation.snippet}"
            </div>

            <div className="pt-2 flex justify-end">
              <button
                onClick={() => setSelectedCitation(null)}
                className="px-3.5 py-1.5 text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
