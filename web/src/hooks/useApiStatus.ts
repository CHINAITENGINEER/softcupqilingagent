import { api } from '../api/client'
import { useDemoResource } from './useDemoResource'

export const useApiStatus = () => useDemoResource(api.health, { status: 'UP' })
