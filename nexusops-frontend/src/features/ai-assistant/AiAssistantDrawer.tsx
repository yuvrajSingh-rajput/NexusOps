import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  X,
  Send,
  Loader2,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FileText,
  Clock,
  ArrowRight,
  ShieldAlert,
  Bot,
  User,
  Plus
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { MarkdownRenderer } from '@/components/common/MarkdownRenderer';
import {
  ApiResponse,
  ChatResponseDto,
  ChatMessageDto,
  ChatCitationDto,
  ActionProposalDto,
  RequestResponseDto
} from '@/types/api';

interface AiAssistantDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  initialPrompt?: string;
}

export const AiAssistantDrawer: React.FC<AiAssistantDrawerProps> = ({
  isOpen,
  onClose,
  initialPrompt = ''
}) => {
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [inputMessage, setInputMessage] = useState(initialPrompt);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [selectedCitation, setSelectedCitation] = useState<ChatCitationDto | null>(null);
  const [actionConfirmingId, setActionConfirmingId] = useState<string | null>(null);
  const [confirmedRequests, setConfirmedRequests] = useState<Record<string, RequestResponseDto>>({});
  const [cancelledActions, setCancelledActions] = useState<Record<string, boolean>>({});
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({});

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (initialPrompt && isOpen) {
      setInputMessage(initialPrompt);
    }
  }, [initialPrompt, isOpen]);

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

  const handleStartNewChat = () => {
    setConversationId(null);
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
        conversationId,
        message: text
      })) as unknown as ApiResponse<ChatResponseDto>;

      if (res && res.data) {
        setConversationId(res.data.conversationId);
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

          // Also update prior proposals in the conversation
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
        content: `⚠️ Failed to get a response: ${err.message || 'Unknown network error'}. Please verify backend service.`,
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
        notes: 'Confirmed via Nexus AI Assistant Drawer'
      })) as unknown as ApiResponse<RequestResponseDto>;

      if (res && res.data) {
        const confirmedData = res.data;
        setConfirmedRequests((prev) => ({
          ...prev,
          [proposal.actionId]: confirmedData
        }));

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
    } catch (err: any) {
      setCancelledActions((prev) => ({ ...prev, [proposal.actionId]: true }));
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <div
        className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs z-50 sm:hidden animate-in fade-in duration-150"
        onClick={onClose}
      />
      <aside className="fixed inset-y-0 right-0 w-full sm:w-[420px] max-w-full bg-white border-l border-slate-200 flex flex-col shadow-2xl z-50 animate-in slide-in-from-right duration-200">
      {/* Top Header */}
      <div className="h-16 px-4 border-b border-slate-200 flex items-center justify-between bg-slate-900 text-white shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="p-1.5 bg-indigo-600 rounded-lg text-white shadow-md shadow-indigo-500/20">
            <Sparkles className="h-4 w-4" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h3 className="text-sm font-semibold tracking-tight text-white">Nexus AI Co-pilot</h3>
              <span className="px-1.5 py-0.5 text-[10px] font-medium bg-indigo-500/30 text-indigo-200 rounded border border-indigo-400/30">
                Governed
              </span>
            </div>
            <p className="text-[11px] text-slate-400">RAG Grounded &amp; Two-Phase Actions</p>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={handleStartNewChat}
            title="Start New Chat"
            className="p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition-colors"
          >
            <Plus className="h-4 w-4" />
          </button>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Message History Container */}
      <div className="flex-1 p-4 pb-24 overflow-y-auto space-y-4 bg-slate-50/50">
        {messages.length === 0 && (
          <div className="space-y-4 my-2">
            <div className="bg-gradient-to-br from-indigo-50 to-white border border-indigo-100 rounded-xl p-4 text-xs text-slate-700 shadow-sm">
              <div className="flex items-center gap-2 text-indigo-700 font-semibold mb-1.5">
                <Bot className="h-4 w-4 text-indigo-600" />
                <span>Enterprise Operations Assistant</span>
              </div>
              <p className="text-slate-600 leading-relaxed">
                I can assist you with company policies, hardware budgets, intake request drafting, and approval tracking.
              </p>
              <div className="mt-2.5 pt-2 border-t border-indigo-100/60 flex items-center gap-2 text-[11px] text-slate-500">
                <ShieldAlert className="h-3.5 w-3.5 text-emerald-600 shrink-0" />
                <span>Strict Security: AI never modifies database records without your explicit two-phase confirmation.</span>
              </div>
            </div>

            <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-1">
              Suggested Operations
            </div>

            <div className="space-y-2">
              <button
                onClick={() => handleSendMessage('What is our developer laptop budget and refresh policy?')}
                className="w-full text-left p-3 text-xs bg-white hover:bg-indigo-50/50 border border-slate-200 hover:border-indigo-200 rounded-lg text-slate-700 transition-all shadow-xs flex items-center justify-between group"
              >
                <span className="font-medium">💻 Developer laptop budget &amp; refresh policy</span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
              </button>

              <button
                onClick={() => handleSendMessage('Show my submitted requests')}
                className="w-full text-left p-3 text-xs bg-white hover:bg-indigo-50/50 border border-slate-200 hover:border-indigo-200 rounded-lg text-slate-700 transition-all shadow-xs flex items-center justify-between group"
              >
                <span className="font-medium">📋 Show my active requests</span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
              </button>

              <button
                onClick={() => handleSendMessage('Show my pending approvals')}
                className="w-full text-left p-3 text-xs bg-white hover:bg-indigo-50/50 border border-slate-200 hover:border-indigo-200 rounded-lg text-slate-700 transition-all shadow-xs flex items-center justify-between group"
              >
                <span className="font-medium">⏳ Check my pending approvals</span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
              </button>

              <button
                onClick={() => handleSendMessage('I need a high performance developer laptop')}
                className="w-full text-left p-3 text-xs bg-white hover:bg-indigo-50/50 border border-slate-200 hover:border-indigo-200 rounded-lg text-slate-700 transition-all shadow-xs flex items-center justify-between group"
              >
                <span className="font-medium">🚀 Propose new laptop request card</span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400 group-hover:text-indigo-600 transition-colors" />
              </button>
            </div>
          </div>
        )}

        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex flex-col ${msg.role === 'USER' ? 'items-end' : 'items-start'} space-y-1`}
          >
            <div className="flex items-center gap-1.5 px-1 text-[11px] text-slate-400">
              {msg.role === 'USER' ? (
                <>
                  <span>You</span>
                  <User className="h-3 w-3" />
                </>
              ) : (
                <>
                  <Bot className="h-3 w-3 text-indigo-600" />
                  <span className="font-medium text-slate-600">Nexus AI</span>
                </>
              )}
            </div>

            <div
              className={`max-w-[88%] rounded-2xl p-3.5 text-xs shadow-xs leading-relaxed ${
                msg.role === 'USER'
                  ? 'bg-indigo-600 text-white rounded-br-none whitespace-pre-wrap'
                  : 'bg-white text-slate-800 border border-slate-200/90 rounded-bl-none'
              }`}
            >
              {msg.role === 'USER' ? (
                msg.content
              ) : (
                <MarkdownRenderer content={msg.content} />
              )}

              {/* Citations section */}
              {msg.citations && msg.citations.length > 0 && (
                <div className="mt-3 pt-2.5 border-t border-slate-100 space-y-1.5">
                  <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                    Grounded Sources:
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {msg.citations.map((c, idx) => (
                      <button
                        key={idx}
                        onClick={() => setSelectedCitation(c)}
                        className="inline-flex items-center gap-1 px-2 py-1 text-[11px] bg-indigo-50 hover:bg-indigo-100 text-indigo-700 rounded-md border border-indigo-200/60 font-medium transition-colors"
                      >
                        <FileText className="h-3 w-3 text-indigo-500" />
                        <span>{c.documentTitle}</span>
                        {c.pageNumber && <span className="text-indigo-400">(p. {c.pageNumber})</span>}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Governed Action Proposal Card */}
            {msg.proposal && (() => {
              const isConfirmed = !!confirmedRequests[msg.proposal.actionId] || msg.proposal.status === 'SUCCEEDED';
              const confirmedNumber = confirmedRequests[msg.proposal.actionId]?.requestNumber || msg.proposal.parameters?.requestNumber;
              const isCancelled = !!cancelledActions[msg.proposal.actionId] || msg.proposal.status === 'REJECTED';

              return (
                <div className={`w-full max-w-[95%] mt-2 rounded-xl p-3.5 shadow-xs space-y-3 border transition-all ${
                  isConfirmed
                    ? 'bg-emerald-50/50 border-emerald-300'
                    : isCancelled
                    ? 'bg-slate-50 border-slate-200'
                    : 'bg-white border-amber-300 shadow-sm'
                }`}>
                  <div className="flex items-start justify-between gap-2 border-b border-amber-100 pb-2.5">
                    <div className="flex items-center gap-2">
                      <div className={`p-1 rounded-md ${isConfirmed ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                        {isConfirmed ? <CheckCircle2 className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
                      </div>
                      <div>
                        <h4 className="text-xs font-semibold text-slate-900">
                          {msg.proposal.title || 'Proposed Operation'}
                        </h4>
                        <p className="text-[11px] text-slate-500 font-mono">Tool: {msg.proposal.toolName}</p>
                      </div>
                    </div>
                    <span className={`px-2 py-0.5 text-[10px] font-semibold rounded-full border ${
                      isConfirmed
                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        : isCancelled
                        ? 'bg-slate-100 text-slate-600 border-slate-200'
                        : 'bg-amber-50 text-amber-700 border-amber-200 animate-pulse'
                    }`}>
                      {isConfirmed ? 'Executed' : isCancelled ? 'Dismissed' : 'Awaiting Review'}
                    </span>
                  </div>

                  <p className="text-xs text-slate-600">{msg.proposal.description}</p>

                  {/* Parameters Preview */}
                  {msg.proposal.parameters && (
                    <div className="bg-slate-50 border border-slate-200/80 rounded-lg p-2.5 text-[11px] font-mono text-slate-700 space-y-1">
                      {Object.entries(msg.proposal.parameters)
                        .filter(([k]) => k !== 'title' && k !== 'categoryCode')
                        .map(([k, v]) => (
                          <div key={k} className="flex justify-between items-center py-0.5 border-b border-slate-100 last:border-b-0">
                            <span className="text-slate-500 capitalize">{k.replace(/([A-Z])/g, ' $1')}:</span>
                            <span className="font-semibold text-slate-900 truncate max-w-[170px]" title={String(v)}>
                              {k.toLowerCase().includes('amount') ? `$${Number(v).toFixed(2)}` : String(v)}
                            </span>
                          </div>
                        ))}
                    </div>
                  )}

                  {!isConfirmed && !isCancelled && (
                    <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                      <Clock className="h-3 w-3 text-amber-600" />
                      <span>Proposal expires in 15 minutes.</span>
                    </div>
                  )}

                  {/* Inline Error Notice */}
                  {actionErrors[msg.proposal.actionId] && (
                    <div className="bg-rose-50 border border-rose-200 rounded-lg p-2.5 flex items-start gap-2 text-xs text-rose-800">
                      <AlertTriangle className="h-4 w-4 text-rose-600 shrink-0 mt-0.5" />
                      <span>{actionErrors[msg.proposal.actionId]}</span>
                    </div>
                  )}

                  {/* Confirm / Cancel Buttons */}
                  {isConfirmed ? (
                    <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-2.5 flex flex-col gap-2 text-xs text-emerald-800">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
                        <div>
                          <span className="font-semibold text-emerald-950">Successfully Executed!</span>
                          <p className="text-[11px] text-emerald-700">
                            Request Number: <strong className="font-mono text-emerald-900">{confirmedNumber || 'Created'}</strong>
                          </p>
                        </div>
                      </div>
                      <Link
                        to="/requests"
                        onClick={onClose}
                        className="inline-flex items-center justify-center gap-1 py-1 px-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-md text-[11px] font-semibold transition-colors"
                      >
                        <span>View in My Requests</span>
                        <ArrowRight className="h-3 w-3" />
                      </Link>
                    </div>
                  ) : isCancelled ? (
                    <div className="bg-slate-100 border border-slate-200 rounded-lg p-2.5 flex items-center gap-2 text-xs text-slate-600">
                      <XCircle className="h-4 w-4 text-slate-400 shrink-0" />
                      <span>Action proposal was cancelled.</span>
                    </div>
                  ) : (
                    <div className="space-y-1.5 pt-1">
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => handleConfirmAction(msg.proposal!)}
                          disabled={actionConfirmingId === msg.proposal.actionId}
                          className="flex-1 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-lg text-xs shadow-xs flex items-center justify-center gap-1.5 transition-colors disabled:opacity-50 cursor-pointer"
                        >
                          {actionConfirmingId === msg.proposal.actionId ? (
                            <>
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              <span>Executing via Backend...</span>
                            </>
                          ) : (
                            <>
                              <CheckCircle2 className="h-3.5 w-3.5" />
                              <span>Confirm &amp; Submit</span>
                            </>
                          )}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleCancelAction(msg.proposal!)}
                          disabled={actionConfirmingId === msg.proposal.actionId}
                          className="py-2 px-3 border border-slate-200 hover:bg-slate-100 text-slate-600 rounded-lg text-xs font-medium transition-colors cursor-pointer"
                        >
                          Dismiss
                        </button>
                      </div>
                      <p className="text-[10px] text-slate-400 text-center">
                        💡 Tip: You can also say <span className="font-semibold text-slate-600">"can you make it work"</span> or <span className="font-semibold text-slate-600">"confirm"</span> in chat!
                      </p>
                    </div>
                  )}
                </div>
              );
            })()}
          </div>
        ))}

        {isSending && (
          <div className="flex items-center gap-2 text-xs text-slate-400 p-2">
            <Loader2 className="h-3.5 w-3.5 animate-spin text-indigo-600" />
            <span>Nexus AI is thinking &amp; verifying policy retrieval...</span>
          </div>
        )}

        <div ref={messagesEndRef} className="h-6 shrink-0" />
      </div>

      {/* Quick Suggestion Chips */}
      <div className="px-3 py-1.5 border-t border-slate-100 bg-slate-50/60 flex items-center gap-1.5 overflow-x-auto text-[10px]">
        <span className="text-slate-400 font-medium shrink-0 flex items-center gap-1">
          <Sparkles className="h-2.5 w-2.5 text-indigo-500" />
          Try:
        </span>
        <button
          type="button"
          onClick={() => handleSendMessage('Create an expense reimbursement request')}
          className="shrink-0 px-2 py-0.5 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
        >
          "Reimbursement request"
        </button>
        <button
          type="button"
          onClick={() => handleSendMessage('I need a high performance developer laptop')}
          className="shrink-0 px-2 py-0.5 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
        >
          "MacBook Pro 16"
        </button>
        <button
          type="button"
          onClick={() => handleSendMessage('Request access to GitHub Copilot Enterprise')}
          className="shrink-0 px-2 py-0.5 bg-white hover:bg-indigo-50 border border-slate-200 hover:border-indigo-300 text-slate-700 hover:text-indigo-700 rounded-full transition-colors font-medium shadow-2xs"
        >
          "GitHub Copilot"
        </button>
      </div>

      {/* Input Form Footer */}
      <div className="p-3.5 border-t border-slate-200 bg-white shrink-0">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendMessage();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={inputMessage}
            onChange={(e) => setInputMessage(e.target.value)}
            disabled={isSending}
            placeholder="Ask policy questions or propose requests..."
            className="flex-1 px-3.5 py-2.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={isSending || !inputMessage.trim()}
            className="p-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl shadow-xs transition-colors"
          >
            <Send className="h-4 w-4" />
          </button>
        </form>
      </div>

      {/* Citation Detail Modal */}
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
                <X className="h-4 w-4" />
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
                Close Excerpt
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  </>
  );
};
