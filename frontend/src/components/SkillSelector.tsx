import React, { useEffect, useState } from 'react';
import { Select, Spin, Tag, Tooltip } from 'antd';
import { BookOutlined } from '@ant-design/icons';
import { getSkills, SkillSummary } from '../api/skill';

interface SkillSelectorProps {
  value?: string;
  onChange?: (value: string | undefined) => void;
  disabled?: boolean;
}

const SkillSelector: React.FC<SkillSelectorProps> = ({ value, onChange, disabled }) => {
  const [skills, setSkills] = useState<SkillSummary[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadSkills();
  }, []);

  const loadSkills = async () => {
    setLoading(true);
    try {
      const response = await getSkills();
      if (response.code === 200 && response.data) {
        setSkills(response.data);
      }
    } catch (error) {
      console.error('Failed to load skills:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (newValue: string | undefined) => {
    onChange?.(newValue);
  };

  return (
    <Spin spinning={loading}>
      <Select
        value={value}
        onChange={handleChange}
        disabled={disabled}
        placeholder="选择一个技能（可选）"
        allowClear
        style={{ width: '100%' }}
        suffixIcon={<BookOutlined />}
      >
        {skills.map((skill) => (
          <Select.Option key={skill.name} value={skill.name}>
            <Tooltip title={skill.description} placement="right">
              <div className="flex items-center gap-2">
                <Tag color="blue">{skill.name}</Tag>
                <span className="text-gray-500 text-xs truncate max-w-48">
                  {skill.description}
                </span>
              </div>
            </Tooltip>
          </Select.Option>
        ))}
      </Select>
    </Spin>
  );
};

export default SkillSelector;
