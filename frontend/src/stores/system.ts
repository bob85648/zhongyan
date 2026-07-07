import { defineStore } from 'pinia'
import { getSystemInfo, type SystemInfo } from '@/api/system'

interface SystemState {
  info: SystemInfo | null
}

export const useSystemStore = defineStore('system', {
  state: (): SystemState => ({
    info: null,
  }),
  actions: {
    async fetchInfo() {
      const response = await getSystemInfo()
      this.info = response.data
      return response.data
    },
  },
})
