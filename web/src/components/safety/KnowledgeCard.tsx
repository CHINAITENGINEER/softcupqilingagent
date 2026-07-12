import type { Knowledge } from '../../api/types'
import { Badge } from '../ui/Badge'
import { Card } from '../ui/Card'
export function KnowledgeCard({ item }: { item: Knowledge }) { return <Card className="knowledge-card"><div><Badge tone="violet">{item.sourceType}</Badge><span className="score">{Math.round(item.score * 100)}% match</span></div><h3>{item.title}</h3><p>{item.snippet}</p><footer><span>{item.metadata?.collection || 'safeops_knowledge'}</span><span>{item.metadata?.tags || 'operations'}</span></footer></Card> }
