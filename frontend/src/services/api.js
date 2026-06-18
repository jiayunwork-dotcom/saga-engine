import axios from 'axios'
import { message } from 'antd'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        localStorage.removeItem('role')
        window.location.href = '/login'
      } else if (error.response.data?.message) {
        message.error(error.response.data.message)
      }
    } else {
      message.error('网络请求失败')
    }
    return Promise.reject(error)
  }
)

export const templateApi = {
  publish: (data) => api.post('/templates/publish', data),
  review: (data) => api.post('/templates/review', data),
  search: (params) => api.get('/templates/search', { params }),
  getDetail: (id) => api.get(`/templates/${id}`),
  getVersions: (name) => api.get(`/templates/${name}/versions`),
  importTemplate: (data) => api.post('/templates/import', data),
  submitRating: (data) => api.post('/templates/rate', data),
  getRatings: (id) => api.get(`/templates/${id}/ratings`)
}

export default api
