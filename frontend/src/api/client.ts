const BASE_URL = '/api/v1'

let authToken: string | null = localStorage.getItem('mrp_token')

export function setToken(token: string | null) {
  authToken = token
  if (token) {
    localStorage.setItem('mrp_token', token)
  } else {
    localStorage.removeItem('mrp_token')
  }
}

export function getToken(): string | null {
  return authToken
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly details?: unknown

  constructor(message: string, status: number, code?: string, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

interface ErrorPayload {
  message?: string
  error?: {
    code?: string
    message?: string
    details?: unknown
  }
}

function redirectToLogin() {
  setToken(null)
  localStorage.removeItem('mrp_user_info')
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const redirect = currentPath && !currentPath.startsWith('/login')
    ? `?redirect=${encodeURIComponent(currentPath)}`
    : ''
  window.location.assign(`/login${redirect}`)
}

async function readError(response: Response): Promise<ApiError> {
  const payload = await response.json().catch(() => ({})) as ErrorPayload
  const backendMessage = payload.error?.message || payload.message

  if (response.status === 401) {
    return new ApiError(backendMessage || '登录已失效，请重新登录', response.status, payload.error?.code, payload.error?.details)
  }
  if (response.status === 403) {
    return new ApiError(backendMessage || '无权限执行此操作，请联系管理员分配权限', response.status, payload.error?.code, payload.error?.details)
  }
  return new ApiError(backendMessage || `HTTP ${response.status}`, response.status, payload.error?.code, payload.error?.details)
}

async function ensureSuccess(response: Response): Promise<void> {
  if (response.ok) return
  const error = await readError(response)
  if (response.status === 401) redirectToLogin()
  throw error
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    throw new ApiError(`接口返回了非 JSON 数据，请检查服务是否已更新及 API 路由：${response.url}`, response.status, 'INVALID_JSON_RESPONSE')
  }
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {})
  }

  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers
  })

  await ensureSuccess(response)

  const result = await readJson(response) as { success?: boolean; data: T; error?: { message?: string } } | null
  // If response has ApiResponse wrapper (success, data), return data directly
  if (result && typeof result === 'object' && 'success' in result) {
    if (!result.success) {
      throw new Error(result.error?.message || '请求失败')
    }
    return result.data
  }
  return result as T
}

export function get<T>(url: string): Promise<T> {
  return request<T>(url)
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>(url, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined
  })
}

export function put<T>(url: string, data: unknown): Promise<T> {
  return request<T>(url, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

export function del<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'DELETE' })
}

export async function download(url: string, filename: string): Promise<void> {
  const headers: Record<string, string> = {}
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }

  const response = await fetch(`${BASE_URL}${url}`, { headers })

  await ensureSuccess(response)

  const blob = await response.blob()
  const downloadUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(downloadUrl)
}

export async function upload<T>(url: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {}
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    method: 'POST',
    headers,
    body: formData
  })

  await ensureSuccess(response)
  const result = await readJson(response) as { success?: boolean; data: T; error?: { message?: string; code?: string; details?: unknown } } | null
  if (result && typeof result === 'object' && 'success' in result) {
    if (!result.success) {
      throw new ApiError(result.error?.message || '上传失败', response.status, result.error?.code, result.error?.details)
    }
    return result.data as T
  }
  return result as T
}

export async function uploadBlob(url: string, formData: FormData, filename: string): Promise<void> {
  const headers: Record<string, string> = {}
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }
  const response = await fetch(`${BASE_URL}${url}`, { method: 'POST', headers, body: formData })
  await ensureSuccess(response)
  const blob = await response.blob()
  const downloadUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(downloadUrl)
}
