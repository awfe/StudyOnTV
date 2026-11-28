import { useEffect, useState, type ReactNode } from 'react';
import { Controller, type UseFormReturn } from 'react-hook-form';
import { ChevronDown, Sparkles } from 'lucide-react';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Field, FieldContent, FieldDescription, FieldGroup, FieldSet, FieldTitle } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import type { Rules } from '@/schema';
import { Choice, FIELD_GROUP_CLASS, Panel } from './RulePrimitives';

const parseVideoLines = (text: string) =>
  text
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean);

type Props = {
  form: UseFormReturn<Rules>;
  c: Rules;
  supervisionEnabled: boolean;
  statusNode?: ReactNode;
  change: (key: any, value: any) => void;
  toggle: (key: any) => ReactNode;
  number: (key: any, min: number, max: number) => ReactNode;
};

export default function TaskRulesTab({
  form,
  c,
  supervisionEnabled,
  statusNode,
  change,
  toggle,
  number,
}: Props) {
  const [videoText, setVideoText] = useState(() => (c.educationalVideos || []).join('\n'));
  const [videoFocused, setVideoFocused] = useState(false);
  const videosKey = (c.educationalVideos || []).join('\n');
  useEffect(() => {
    if (!videoFocused) setVideoText(videosKey);
  }, [videosKey, videoFocused]);

  const rewardAndQuotaFields = (
    <FieldGroup className={FIELD_GROUP_CLASS}>
      <Field orientation="responsive" className="py-3.5">
        <FieldContent>
          <FieldTitle>按题目星级奖励</FieldTitle>
          <FieldDescription className="text-xs leading-5">
            关闭时：通过一组固定解锁 N 分钟；开启时：按题目难度星级分别设置奖励时长
          </FieldDescription>
        </FieldContent>
        {toggle('difficultyRewardEnabled')}
      </Field>

      {c.difficultyRewardEnabled ? (
        [1, 2, 3, 4, 5].map(star => (
          <Field key={star} orientation="responsive" className="py-3.5">
            <FieldContent>
              <FieldTitle>{'★'.repeat(star)} 每题奖励（分钟）</FieldTitle>
            </FieldContent>
            <Input
              className="w-24 text-center"
              aria-label={star + '星奖励'}
              type="number"
              min={0.1}
              max={120}
              step={0.1}
              {...form.register(`difficultyRewards.${star}`, { valueAsNumber: true })}
            />
          </Field>
        ))
      ) : (
        <Field orientation="responsive" className="py-3.5">
          <FieldContent>
            <FieldTitle>通过一组解锁（分钟）</FieldTitle>
          </FieldContent>
          {number('sessionMinutes', 1, 120)}
        </Field>
      )}

      <Field orientation="responsive" className="py-3.5">
        <FieldContent>
          <FieldTitle>每日使用上限（分钟）</FieldTitle>
          <FieldDescription className="text-xs leading-5">超出后当天锁死；0 点重置</FieldDescription>
        </FieldContent>
        <div className="flex items-center gap-3">
          {toggle('dailyLimitEnabled')}
          {c.dailyLimitEnabled && number('dailyMinutes', 1, 480)}
        </div>
      </Field>

      <Field orientation="responsive" className="py-3.5">
        <FieldContent>
          <FieldTitle>每天最多解锁（组）</FieldTitle>
          <FieldDescription className="text-xs leading-5">答完上限即锁定</FieldDescription>
        </FieldContent>
        <div className="flex items-center gap-3">
          {toggle('roundsLimitEnabled')}
          {c.roundsLimitEnabled && number('roundsPerDay', 1, 100)}
        </div>
      </Field>
    </FieldGroup>
  );

  return (
    <Panel title="学习任务与背诵策略" icon={Sparkles} status={statusNode}>
      <FieldSet className="gap-0">
        <FieldGroup className={FIELD_GROUP_CLASS}>
          <Field orientation="responsive" className="py-3.5">
            <FieldContent>
              <FieldTitle>玩法</FieldTitle>
              <FieldDescription className="text-xs leading-5">
                按组闯关：答完一组结算；连续答对：连对 N 题达标；自由练习：不计解锁，随时刷题；答对指定题数：累计答对 N 题达标
              </FieldDescription>
            </FieldContent>
            <Controller
              name="playMode"
              control={form.control}
              render={({ field }) => (
                <Choice
                  value={field.value}
                  onChange={field.onChange}
                  label="玩法"
                  options={[
                    ['round', '按组闯关'],
                    ['streak', '连续答对'],
                    ['practice', '自由练习'],
                    ['count', '答对指定题数'],
                  ]}
                />
              )}
            />
          </Field>

          <Field orientation="responsive" className="py-3.5">
            <FieldContent>
              <FieldTitle>
                {c.playMode === 'streak'
                  ? '连续答对目标（题）'
                  : c.playMode === 'count'
                    ? '累计答对目标（题）'
                    : '每组题量'}
              </FieldTitle>
            </FieldContent>
            {number('questionsPerRound', 1, 100)}
          </Field>

          <Field orientation="responsive" className="py-3.5">
            <FieldContent>
              <FieldTitle>先背再测</FieldTitle>
              <FieldDescription className="text-xs leading-5">
                做题前先展示答案与解析卡片背诵一遍，再打乱测验，适合生词、古诗文与新题入门
              </FieldDescription>
            </FieldContent>
            {toggle('memorizeFirst')}
          </Field>

          {supervisionEnabled && rewardAndQuotaFields}
        </FieldGroup>
      </FieldSet>

      {!supervisionEnabled && (
        <Collapsible className="border-t border-border/60">
          <CollapsibleTrigger
            type="button"
            className="group flex items-center gap-1.5 py-3.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            <ChevronDown className="size-3 transition-transform group-data-[state=open]:rotate-180" />
            屏幕时长与奖励规则（切换到「先学再看管控模式」后生效）
          </CollapsibleTrigger>
          <CollapsibleContent>{rewardAndQuotaFields}</CollapsibleContent>
        </Collapsible>
      )}

      <Collapsible className="border-t border-border/60">
        <CollapsibleTrigger
          type="button"
          className="group flex items-center gap-1.5 py-3.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          <ChevronDown className="size-3 transition-transform group-data-[state=open]:rotate-180" />
          更多答题与显示设置
        </CollapsibleTrigger>
        <CollapsibleContent>
          <FieldSet className="gap-0">
            <FieldGroup className={FIELD_GROUP_CLASS}>
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>学习内容</FieldTitle>
                </FieldContent>
                <Controller
                  name="learningContent"
                  control={form.control}
                  render={({ field }) => (
                    <Choice
                      label="学习内容"
                      value={field.value}
                      onChange={field.onChange}
                      options={[
                        ['quiz', '答题'],
                        ['video', '公益视频'],
                        ['alternate', '视频与答题交替'],
                      ]}
                    />
                  )}
                />
              </Field>

              {c.learningContent !== 'quiz' && (
                <>
                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>每次视频学习（分钟）</FieldTitle>
                    </FieldContent>
                    {number('videoMinutes', 1, 30)}
                  </Field>
                  <Field className="py-3.5">
                    <FieldContent>
                      <FieldTitle>公益视频直链</FieldTitle>
                      <FieldDescription className="text-xs leading-5">每行一个 HTTP/HTTPS 地址</FieldDescription>
                    </FieldContent>
                    <Textarea
                      aria-label="公益视频直链"
                      rows={4}
                      value={videoText}
                      onFocus={() => setVideoFocused(true)}
                      onChange={e => {
                        const raw = e.target.value;
                        setVideoText(raw);
                        change('educationalVideos', parseVideoLines(raw));
                      }}
                      onBlur={e => {
                        setVideoFocused(false);
                        setVideoText(parseVideoLines(e.target.value).join('\n'));
                      }}
                    />
                  </Field>
                </>
              )}

              {supervisionEnabled && (
                <>
                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>每日免费时长（分钟）</FieldTitle>
                      <FieldDescription className="text-xs leading-5">到期前无需答题；0 为关闭</FieldDescription>
                    </FieldContent>
                    {number('dailyFreeMinutes', 0, 120)}
                  </Field>

                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>每次开机免费（分钟）</FieldTitle>
                      <FieldDescription className="text-xs leading-5">
                        真实开机或唤醒时发放，重开应用不重复；0 为关闭
                      </FieldDescription>
                    </FieldContent>
                    {number('bootFreeMinutes', 0, 120)}
                  </Field>
                </>
              )}

              {c.bootFreeMinutes > 0 && (
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>开机间隔（分钟）</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      开机或唤醒时，距上次发放至少这么久才会再次发放；0 为不限
                    </FieldDescription>
                  </FieldContent>
                  {number('bootFreeIntervalMinutes', 0, 1440)}
                </Field>
              )}

              {c.playMode === 'round' && (
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>通过正确率（%）</FieldTitle>
                  </FieldContent>
                  <Controller
                    name="passRatio"
                    control={form.control}
                    render={({ field }) => (
                      <Input
                        className="w-24 text-center"
                        type="number"
                        min={1}
                        max={100}
                        aria-label="通过正确率"
                        value={Number.isFinite(field.value) ? Math.round(Number(field.value) * 100) : ''}
                        onChange={e =>
                          field.onChange(e.target.value === '' ? NaN : Number(e.target.value) / 100)
                        }
                      />
                    )}
                  />
                </Field>
              )}

              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>答错后强制查看解析（秒）</FieldTitle>
                  <FieldDescription className="text-xs leading-5">0 为不强制；期间不能提前进入下一题</FieldDescription>
                </FieldContent>
                {number('wrongReviewSeconds', 0, 300)}
              </Field>

              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>随机打乱选项</FieldTitle>
                  <FieldDescription className="text-xs leading-5">打乱单选、多选选项；判断题不变</FieldDescription>
                </FieldContent>
                {toggle('shuffleOptions')}
              </Field>

              {supervisionEnabled && (
                <>
                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>显示剩余时间角标</FieldTitle>
                      <FieldDescription className="text-xs leading-5">
                        看视频时在屏幕角落显示剩余分钟；最后一分钟闪烁
                      </FieldDescription>
                    </FieldContent>
                    {toggle('showCountdown')}
                  </Field>
                  {c.showCountdown && (
                    <Field orientation="responsive" className="py-3.5">
                      <FieldContent>
                        <FieldTitle>倒计时角标位置</FieldTitle>
                        <FieldDescription className="text-xs leading-5">
                          可避开电视右上角默认时间或底部字幕位置
                        </FieldDescription>
                      </FieldContent>
                      <Controller
                        name="countdownPosition"
                        control={form.control}
                        render={({ field }) => (
                          <Choice
                            label="倒计时角标位置"
                            value={field.value}
                            onChange={field.onChange}
                            options={[
                              ['top-right', '右上角'],
                              ['top-left', '左上角'],
                              ['bottom-right', '右下角'],
                              ['bottom-left', '左下角'],
                            ]}
                          />
                        )}
                      />
                    </Field>
                  )}
                </>
              )}

              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>学习页字号（%）</FieldTitle>
                  <FieldDescription className="text-xs leading-5">75–200</FieldDescription>
                </FieldContent>
                {number('fontScale', 75, 200)}
              </Field>
            </FieldGroup>
          </FieldSet>
        </CollapsibleContent>
      </Collapsible>
    </Panel>
  );
}
