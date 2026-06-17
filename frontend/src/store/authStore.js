import { create } from 'zustand'
import api from '../services/api'

export const useAuthStore = create((set) => ({
  isAuthenticated: !!localStorage.getItem('token'),
  token: localStorage.getItem('token'),
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  role: localStorage.getItem('role'),

  login: async (username, password) => {
    const response = await api.post('/auth/login', { username, password })
    const { token, role, email } = response.data.data
    
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify({ username, email }))
    localStorage.setItem('role', role)
    
    set({
      isAuthenticated: true,
      token,
      user: { username, email },
      role
    })
    
    return response.data
  },

  logout: () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('role')
    
    set({
      isAuthenticated: false,
      token: null,
      user: null,
      role: null
    })
  },

  isAdmin: () => {
    return localStorage.getItem('role') === 'ADMIN'
  }
}))
