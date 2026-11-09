import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface BreadcrumbEntry {
  path: string
  label: string
}

export const useBreadcrumbStore = defineStore('breadcrumb', () => {
  const history = ref<BreadcrumbEntry[]>([])

  function loadFromStorage() {
    try {
      const stored = localStorage.getItem('mrp-breadcrumb-history')
      if (stored) {
        history.value = JSON.parse(stored)
      }
    } catch {
      history.value = []
    }
  }

  function saveToStorage() {
    localStorage.setItem('mrp-breadcrumb-history', JSON.stringify(history.value))
  }

  const MAX_HISTORY = 6

  function pushPath(path: string, label: string) {
    const existing = history.value.findIndex(entry => entry.path === path)
    if (existing >= 0) {
      // Revisiting a recorded page returns to that point: drop everything after it.
      history.value = history.value.slice(0, existing + 1)
      history.value[existing] = { path, label }
    } else {
      history.value = [...history.value, { path, label }].slice(-MAX_HISTORY)
    }
    saveToStorage()
  }

  function clear() {
    history.value = []
    localStorage.removeItem('mrp-breadcrumb-history')
  }

  loadFromStorage()

  return {
    history,
    pushPath,
    clear
  }
})
