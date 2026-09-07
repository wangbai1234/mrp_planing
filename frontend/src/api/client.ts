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

  if (response.status === 401 || response.status === 403) {
    setToken(null)
    window.location.href = '/login'
    throw new Error('未授权，请重新登录')
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(error?.error?.message || error?.message || `HTTP ${response.status}`)
  }

  return response.json()
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

  if (response.status === 401 || response.status === 403) {
    setToken(null)
    window.location.href = '/login'
    throw new Error('未授权')
  }

  if (!response.ok) {
    throw new Error(`下载失败: ${response.statusText}`)
  }

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

export function upload<T>(url: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {}
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }

  return fetch(`${BASE_URL}${url}`, {
    method: 'POST',
    headers,
    body: formData
  }).then(r => {
    if (r.status === 401 || r.status === 403) {
      setToken(null)
      window.location.href = '/login'
      throw new Error('未授权')
    }
    return r.json()
  })
}
