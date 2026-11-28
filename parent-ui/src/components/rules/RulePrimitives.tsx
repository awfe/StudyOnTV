import { useEffect, useState, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { SectionHeading } from '@/components/ui/section-heading';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export const FIELD_GROUP_CLASS =
  'gap-0 [&>[data-slot=field]+[data-slot=field]]:border-t [&>[data-slot=field]+[data-slot=field]]:border-border/60';

export const fieldLabels: Record<string, string> = {
  supervisionEnabled: '启用屏幕时间管控',
  memorizeFirst: '先背再测',
  spacedScope: '间隔重复范围',
  videoMinutes: '每次视频学习分钟',
  dailyFreeMinutes: '每日免费时长',
  bootFreeMinutes: '每次开机或唤醒免费时长',
  bootFreeIntervalMinutes: '开机间隔',
  dailyMinutes: '每日使用上限',
  questionsPerRound: '题量',
  roundsPerDay: '每天最多解锁组数',
  wrongReviewSeconds: '答错后查看解析秒数',
  fontScale: '学习页字号',
  restWatchMinutes: '累计观看分钟',
  restMinutes: '强制休息分钟',
  sessionMinutes: '通过一组解锁分钟',
  wrongThreshold: '累计答错题数',
  extraQuestions: '每次增加加练题数',
  dailyLimitEnabled: '启用每日时长上限',
  shuffleOptions: '随机打乱选项',
  showCountdown: '显示剩余时间角标',
  roundsLimitEnabled: '启用每日组数上限',
  restEnabled: '强制休息',
  trafficAssistEnabled: '按流量判断是否在播放',
  backgroundAnchorEnabled: '保留后台空窗口',
  blockSettingsDuringControl: '管控期间不允许进系统设置与应用管理',
  allDay: '全天管控',
  childSelectLevel: '孩子选择年级',
  difficultyRewardEnabled: '按题目星级奖励',
  extraEnabled: '答错累计加练',
  spacedRepetitionEnabled: '按记忆曲线复习',
  newFirst: '没做过的题先出',
  wrongFirst: '答错的题先出',
  coverageFirst: '没答对的题先出',
  allowQuizUnlock: '答题页输密码解锁',
};

const errorNames: Record<string, string> = {
  timePoints: '网课时段',
  exemptWindows: '大人豁免',
  watchWhitelist: '不计时应用',
};

const localizeMessage = (message: string) =>
  message
    .replace(/^Number must be greater than or equal to (.+)$/, '不能小于 $1')
    .replace(/^Number must be less than or equal to (.+)$/, '不能大于 $1')
    .replace(/^Number must be greater than (.+)$/, '必须大于 $1')
    .replace(/^Expected number, received nan$/, '请输入有效数字');

/** 把 zod 的嵌套错误拍平成「位置：中文提示」，供页面底部列表和自动保存失败提示共用。 */
export function collectErrorMessages(errors: any) {
  const out: { key: string; text: string }[] = [];
  const walk = (path: (string | number)[], error: any) => {
    if (!error) return;
    if (typeof error.message === 'string' && error.message) {
      const label = path
        .map((part, index) => {
          if (path[0] === 'difficultyRewards' && index === 1) return `${Number(part)} 星奖励`;
          return typeof part === 'number' ? `第 ${part + 1} 条` : errorNames[part] || '';
        })
        .filter(Boolean)
        .join(' ');
      const message = localizeMessage(error.message);
      out.push({ key: path.join('.'), text: label ? label + '：' + message : message });
      return;
    }
    if (Array.isArray(error)) {
      error.forEach((item, index) => walk([...path, index], item));
      return;
    }
    if (typeof error === 'object') {
      const entries = Object.entries(error);
      if (!entries.length) {
        out.push({ key: path.join('.'), text: '请检查 ' + (path.join('.') || '设置') });
        return;
      }
      entries.forEach(([key, value]) => walk([...path, key], value));
      return;
    }
    out.push({ key: path.join('.'), text: '请检查 ' + path.join('.') });
  };
  Object.entries(errors || {}).forEach(([key, error]) => walk([key], error));
  return out;
}

/** 数字步进器：输入过程中先保留原始文本，失焦时再夹到 min–max。
 *  直接在 onChange 里夹紧会把「8」立刻改成 75，再输入「0」就变成 750 → 200。 */
export function NumberStepper({
  value,
  onChange,
  min,
  max,
  label,
}: {
  value: number;
  onChange: (value: number) => void;
  min: number;
  max: number;
  label: string;
}) {
  const clamp = (n: number) => (Number.isNaN(n) ? min : Math.min(max, Math.max(min, n)));
  const [text, setText] = useState(String(value));
  const [focused, setFocused] = useState(false);
  useEffect(() => {
    if (!focused) setText(String(value));
  }, [value, focused]);
  const commit = (raw: string) => {
    const next = clamp(raw.trim() === '' ? min : Number(raw));
    setText(String(next));
    if (next !== value) onChange(next);
  };
  const bump = (delta: number) => {
    const base = focused ? (text.trim() === '' ? min : Number(text)) : value;
    const next = clamp(base + delta);
    setText(String(next));
    onChange(next);
  };
  return (
    <div className="flex items-center gap-1">
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="size-8"
        aria-label={'减少' + label}
        onClick={() => bump(-1)}
      >
        −
      </Button>
      <Input
        aria-label={label}
        className="h-8 w-14 text-center"
        type="number"
        inputMode="numeric"
        min={min}
        max={max}
        value={text}
        onFocus={() => setFocused(true)}
        onChange={e => {
          const raw = e.target.value;
          setText(raw);
          const n = Number(raw);
          if (raw.trim() !== '' && !Number.isNaN(n) && n >= min && n <= max) onChange(n);
        }}
        onBlur={e => {
          setFocused(false);
          commit(e.target.value);
        }}
        onKeyDown={e => {
          if (e.key === 'Enter') {
            e.preventDefault();
            e.currentTarget.blur();
          }
        }}
      />
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="size-8"
        aria-label={'增加' + label}
        onClick={() => bump(1)}
      >
        +
      </Button>
    </div>
  );
}

export function Choice({
  value,
  onChange,
  options,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  options: [string, string][];
  label?: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label={label} className="min-w-32">
        <SelectValue>{options.find(([v]) => v === value)?.[1] || '请选择'}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        {options.map(([v, t]) => (
          <SelectItem key={v} value={v}>
            {t}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/** 后台保活等旧面板使用的行布局；保留与上游相同的 title/note/children 接口。 */
export function Row({
  title,
  note,
  children,
}: {
  title: string;
  note?: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border/60 py-3.5 last:border-0">
      <div className="min-w-0">
        <div className="text-sm font-medium">{title}</div>
        {note && <div className="mt-1 text-xs leading-5 text-muted-foreground">{note}</div>}
      </div>
      {children != null && <div className="shrink-0 min-w-0">{children}</div>}
    </div>
  );
}

export function Panel({
  title,
  description,
  hint,
  children,
  footer,
  icon: Icon,
  status,
}: {
  title: string;
  description?: string;
  hint?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  icon?: any;
  status?: ReactNode;
}) {
  return (
    <section className="space-y-2">
      <SectionHeading
        icon={Icon}
        title={title}
        description={description}
        action={
          <>
            {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
            {status}
          </>
        }
      />
      <Card className="gap-0 overflow-hidden border-border/60 py-0 shadow-sm">
        <CardContent className="px-4 sm:px-5">{children}</CardContent>
        {footer && <div className="flex justify-end gap-2 border-t border-border/60 py-3">{footer}</div>}
      </Card>
    </section>
  );
}
