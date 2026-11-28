import { BookOpen, ChartColumn, Check, Flame, GraduationCap, Repeat2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

type Props = {
  supervisionEnabled: boolean;
  quickStudy: boolean;
  study: any;
  questionCount?: number;
  pending: boolean;
  onPatchConfig: (patch: Record<string, any>) => void;
};

export default function OperationModeCard({
  supervisionEnabled,
  quickStudy,
  study,
  questionCount,
  pending,
  onPatchConfig,
}: Props) {
  return (
    <Card className="gap-3 border-primary/20 bg-gradient-to-br from-primary/[0.04] via-card to-card py-4 shadow-xs">
      <CardHeader className="pb-0">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-sm">
            <GraduationCap className="size-4 text-primary" />
            运行模式选择
          </CardTitle>
          {Number(study.streakDays) > 0 && (
            <Badge variant="secondary" className="gap-1">
              <Flame className="size-3.5 text-amber-500" />
              连续学习 {study.streakDays} 天
            </Badge>
          )}
        </div>
        <CardDescription className="text-xs">
          无需繁琐系统权限也能直接当成 Anki / 多邻国式智能错题本与背诵提分工具使用，随时一键切换。
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid gap-2.5 sm:grid-cols-2">
          <button
            type="button"
            disabled={pending}
            onClick={() => {
              if (supervisionEnabled) onPatchConfig({ supervisionEnabled: false });
            }}
            className={`flex flex-col items-start gap-1 rounded-xl border p-3.5 text-left transition-colors ${
              !supervisionEnabled
                ? 'border-primary bg-primary/5 ring-1 ring-primary/30'
                : 'border-border/70 bg-background/60 hover:bg-muted/40'
            }`}
          >
            <div className="flex w-full items-center justify-between gap-2">
              <span className="text-sm font-semibold">📚 纯粹学习提分模式</span>
              {!supervisionEnabled && (
                <Badge variant="default" className="h-5 px-1.5 text-[11px]">
                  <Check className="me-0.5 size-3" />
                  当前启用
                </Badge>
              )}
            </div>
            <span className="text-xs leading-5 text-muted-foreground">
              免锁屏、免复杂权限，打开即是闪卡背诵、错题先背再测与艾宾浩斯复习中心
            </span>
          </button>
          <button
            type="button"
            disabled={pending}
            onClick={() => {
              if (!supervisionEnabled) onPatchConfig({ supervisionEnabled: true });
            }}
            className={`flex flex-col items-start gap-1 rounded-xl border p-3.5 text-left transition-colors ${
              supervisionEnabled
                ? 'border-primary bg-primary/5 ring-1 ring-primary/30'
                : 'border-border/70 bg-background/60 hover:bg-muted/40'
            }`}
          >
            <div className="flex w-full items-center justify-between gap-2">
              <span className="text-sm font-semibold">⏳ 先学再看管控模式</span>
              {supervisionEnabled && (
                <Badge variant="default" className="h-5 px-1.5 text-[11px]">
                  <Check className="me-0.5 size-3" />
                  当前启用
                </Badge>
              )}
            </div>
            <span className="text-xs leading-5 text-muted-foreground">
              答题达标赢取屏幕使用时间，到点自动提醒休息与学习
            </span>
          </button>
        </div>

        {!supervisionEnabled && (
          <div className="rounded-xl border border-border/70 bg-background/80 p-3.5">
            <div className="mb-2.5 flex flex-wrap items-center justify-between gap-2">
              <span className="text-xs font-semibold text-foreground">🚀 3 步快速提分上手</span>
              <span className="text-xs text-muted-foreground">
                今日已练 {study.todayAnswered || 0} 题 · 待复习 {study.dueCount || 0} 题 ·{' '}
                <button
                  type="button"
                  className="font-medium text-primary underline-offset-4 hover:underline"
                  onClick={() => (location.hash = '/wrong')}
                >
                  错题 {study.wrongCount || 0} 道
                </button>
              </span>
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              <button
                type="button"
                onClick={() => {
                  location.hash = '/bank';
                }}
                className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-card px-3 py-2.5 text-left text-xs transition-colors hover:bg-accent"
              >
                <div>
                  <div className="font-medium">1. 导入题库或 AI 出题</div>
                  <div className="mt-0.5 text-[11px] text-muted-foreground">
                    {questionCount ? `已有 ${questionCount} 题` : '10 秒模板生成'}
                  </div>
                </div>
                <BookOpen className="size-4 shrink-0 text-primary" />
              </button>
              <button
                type="button"
                disabled={pending}
                onClick={() => {
                  if (!quickStudy) {
                    onPatchConfig({
                      memorizeFirst: true,
                      spacedRepetitionEnabled: true,
                    });
                  } else {
                    location.hash = '/rules';
                  }
                }}
                className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-card px-3 py-2.5 text-left text-xs transition-colors hover:bg-accent"
              >
                <div>
                  <div className="font-medium">2. 开启先背再测 / 间隔重复</div>
                  <div className="mt-0.5 text-[11px] text-muted-foreground">
                    {quickStudy ? '已开启记忆提分策略 ✓' : '点击一键开启科学背题'}
                  </div>
                </div>
                <Repeat2 className="size-4 shrink-0 text-primary" />
              </button>
              <button
                type="button"
                onClick={() => {
                  location.hash = '/records';
                }}
                className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-card px-3 py-2.5 text-left text-xs transition-colors hover:bg-accent"
              >
                <div>
                  <div className="font-medium">3. 学情页追踪薄弱项</div>
                  <div className="mt-0.5 text-[11px] text-muted-foreground">
                    查看错题榜并一键举一反三
                  </div>
                </div>
                <ChartColumn className="size-4 shrink-0 text-primary" />
              </button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
