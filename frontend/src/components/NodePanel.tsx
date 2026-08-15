import { useEffect, useState } from 'react';
import { Collapse, Input, Tag, message } from 'antd';
import { HolderOutlined, SearchOutlined } from '@ant-design/icons';
import { getNodeTypes, NodeDefinition } from '../api/workflow';

interface NodePanelProps {
  onDragStart: (event: React.DragEvent, nodeType: string, displayName: string) => void;
}

/**
 * 左侧节点面板组件
 */
const NodePanel = ({ onDragStart }: NodePanelProps) => {
  const [nodeTypes, setNodeTypes] = useState<NodeDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    loadNodeTypes();
  }, []);

  const loadNodeTypes = async () => {
    setLoading(true);
    try {
      const result = await getNodeTypes();
      console.log('Node types API result:', result);
      if (result.code === 200) {
        setNodeTypes(result.data);
      } else {
        console.error('Failed to load node types:', result);
        message.error(`加载节点类型失败: ${result.message || '未知错误'}`);
      }
    } catch (error) {
      console.error('Error loading node types:', error);
      message.error(`加载节点类型失败: ${error instanceof Error ? error.message : '网络错误'}`);
    } finally {
      setLoading(false);
    }
  };

  // 按分类分组节点
  const normalizedSearchTerm = searchTerm.trim().toLowerCase();
  const visibleNodeTypes = normalizedSearchTerm
    ? nodeTypes.filter((node) => (
        node.displayName.toLowerCase().includes(normalizedSearchTerm)
        || node.nodeType.toLowerCase().includes(normalizedSearchTerm)
      ))
    : nodeTypes;
  const llmNodes = visibleNodeTypes.filter((node) => node.category === 'LLM');
  const toolNodes = visibleNodeTypes.filter((node) => node.category === 'TOOL');
  const controlNodes = visibleNodeTypes.filter((node) => node.category === 'CONTROL');

  const getNodeTone = (node: NodeDefinition) => {
    if (node.nodeType === 'input') return 'node-library-green';
    if (node.nodeType === 'output') return 'node-library-purple';
    if (node.category === 'TOOL') return 'node-library-amber';
    if (node.category === 'CONTROL') return 'node-library-purple';
    return 'node-library-blue';
  };

  const renderNodeItem = (node: NodeDefinition) => (
    <div
      key={node.nodeType}
      draggable
      onDragStart={(e) => onDragStart(e, node.nodeType, node.displayName)}
      className="node-library-item"
    >
      <div className={`node-library-icon ${getNodeTone(node)}`}>{node.icon}</div>
      <div className="min-w-0 flex-1">
        <div className="node-library-title">{node.displayName}</div>
        <div className="node-library-meta">{node.nodeType}</div>
      </div>
      <span className="node-library-drag" aria-hidden="true">
        <HolderOutlined />
      </span>
    </div>
  );

  const items = [
    {
      key: 'llm',
      label: (
        <div className="node-library-section-title">
          <span>大模型节点</span>
          <Tag color="blue">{llmNodes.length}</Tag>
        </div>
      ),
      children: (
        <div className="space-y-2">
          {llmNodes.length > 0 ? (
            llmNodes.map(renderNodeItem)
          ) : (
            <div className="text-gray-400 text-center py-4">暂无节点</div>
          )}
        </div>
      ),
    },
    {
      key: 'tool',
      label: (
        <div className="node-library-section-title">
          <span>工具节点</span>
          <Tag color="gold">{toolNodes.length}</Tag>
        </div>
      ),
      children: (
        <div className="space-y-2">
          {toolNodes.length > 0 ? (
            toolNodes.map(renderNodeItem)
          ) : (
            <div className="text-gray-400 text-center py-4">暂无节点</div>
          )}
        </div>
      ),
    },
    ...(controlNodes.length > 0
      ? [
          {
            key: 'control',
            label: (
              <div className="node-library-section-title">
                <span>控制节点</span>
                <Tag color="purple">{controlNodes.length}</Tag>
              </div>
            ),
            children: (
              <div className="space-y-2">
                {controlNodes.map(renderNodeItem)}
              </div>
            ),
          },
        ]
      : []),
  ].filter((item) => (
    !normalizedSearchTerm
    || (item.key === 'llm' && llmNodes.length > 0)
    || (item.key === 'tool' && toolNodes.length > 0)
    || (item.key === 'control' && controlNodes.length > 0)
  ));

  return (
    <div className="h-full flex flex-col overflow-hidden">
      <div className="node-library-header">
        <div>
          <h3 className="node-library-heading">节点库</h3>
        </div>
      </div>
      <div className="node-library-body">
        <Input
          allowClear
          className="node-library-search"
          placeholder="搜索节点"
          prefix={<SearchOutlined />}
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
        />
        {loading ? (
          <div className="text-center py-8 text-gray-400">加载中...</div>
        ) : (
          <>
            <Collapse
              defaultActiveKey={['llm', 'tool', 'control']}
              ghost
              items={items}
              bordered={false}
            />
            {visibleNodeTypes.length === 0 && (
              <div className="node-library-empty">没有匹配的节点</div>
            )}
            <div className="node-library-tip">拖拽组件，连接并配置执行路径</div>
          </>
        )}
      </div>
    </div>
  );
};

export default NodePanel;
