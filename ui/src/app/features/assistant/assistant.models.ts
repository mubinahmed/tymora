/** AssistantInterface (server) mirrored for the REST bridge. */
export interface AssistantEntry {
  role: 'user' | 'assistant' | 'tool' | 'notice';
  text?: string;
  tool?: string;
  detail?: string;
  error?: boolean;
  linkPath?: string;
  linkLabel?: string;
}

export interface AssistantPendingAction {
  tool: string;
  title: string;
  summary: string;
}

export interface AssistantRequest {
  operation: 'STATUS' | 'SEND' | 'CONFIRM' | 'RESET';
  conversationId?: string;
  text?: string;
  approve?: boolean;
  page?: string;
}

export interface AssistantResponse {
  enabled?: boolean;
  backend?: string;
  model?: string;
  unavailableReason?: string;
  conversationId?: string;
  entries?: AssistantEntry[];
  status?: 'answer' | 'confirm' | 'refused' | 'truncated' | 'limit' | 'error';
  pending?: AssistantPendingAction[];
  servedBy?: string;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number;
  suggestions?: string[];
}
