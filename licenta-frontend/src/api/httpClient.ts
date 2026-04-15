import axios from 'axios'
import { getStoredToken } from '../auth/tokenStorage'

const httpClient = axios.create({
  baseURL: 'http://localhost:8080',
})

httpClient.interceptors.request.use((config) => {
  const token = getStoredToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export default httpClient
