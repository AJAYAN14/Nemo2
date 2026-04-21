import { useMemo } from 'react';

/**
 * iOS HIG 风格配色列表 - 用于生成随机但稳定的颜色
 * 与 Android 端 Nemo 项目保持 1:1 同步
 */
export const HIG_COLORS = [
  '#007AFF', // Blue
  '#34C759', // Green
  '#FF9500', // Orange
  '#FF2D55', // Pink
  '#5856D6', // Indigo
  '#AF52DE', // Purple
  '#00C7BE', // Mint
  '#FF3B30', // Red
  '#5AC8FA', // Teal
  '#FFCC00', // Yellow
];

interface ColorPair {
  primary: string;
  background: string;
}

/**
 * 根据 ID 获取稳定的随机颜色 Hook
 * @param id 单词或语法的 ID
 * @returns 包含主色和背景色的对象
 */
export function useHigColors(id: string | number): ColorPair {
  return useMemo(() => {
    // 简单的 Hash 算法，确保相同的 ID 得到相同的颜色
    let hash = 0;
    const strId = String(id);
    for (let i = 0; i < strId.length; i++) {
        hash = strId.charCodeAt(i) + ((hash << 5) - hash);
    }
    
    const index = Math.abs(hash) % HIG_COLORS.length;
    const primary = HIG_COLORS[index];
    
    // 背景色使用主色的 10% 透明度 (浅色模式) 
    // 注意：这里为了简化使用 hex 直接拼接，实际可能需要更复杂的转换
    const background = `${primary}1A`; // 1A is ~10% opacity in hex
    
    return {
      primary,
      background,
    };
  }, [id]);
}
