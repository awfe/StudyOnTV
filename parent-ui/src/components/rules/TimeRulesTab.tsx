import type { ReactNode } from 'react';
import { Controller, type UseFormReturn } from 'react-hook-form';
import { ChevronDown, Clock3 } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Field, FieldContent, FieldDescription, FieldGroup, FieldSet, FieldTitle } from '@/components/ui/field';
import { zoneOptions, type Rules } from '@/schema';
import TimePoints from '@/TimePoints';
import WatchWhitelistField from '@/WatchWhitelistField';
import WeekSchedule from '@/WeekSchedule';
import { Choice, FIELD_GROUP_CLASS, Panel } from './RulePrimitives';

type Props = {
  form: UseFormReturn<Rules>;
  c: Rules;
  supervisionEnabled: boolean;
  deviceData: any;
  statusNode?: ReactNode;
  change: (key: any, value: any) => void;
  toggle: (key: any) => ReactNode;
  number: (key: any, min: number, max: number) => ReactNode;
};

export default function TimeRulesTab({
  form,
  c,
  supervisionEnabled,
  deviceData,
  statusNode,
  change,
  toggle,
  number,
}: Props) {
  // Radix 的 SelectItem 不接受空字符串，所以「跟随设备」在控件里用 device 代替
  const zoneChoices = zoneOptions.map(([value, label]) => [value || 'device', label] as [string, string]);

  return (
    <>
      {!supervisionEnabled && (
        <Alert>
          <Clock3 />
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>当前处于「纯粹学习提分模式」，设备不会锁屏或限制其他应用。如需限制观看时长，请开启管控模式。</span>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="ms-auto"
              onClick={() => change('supervisionEnabled', true)}
            >
              开启先学再看管控
            </Button>
          </AlertDescription>
        </Alert>
      )}
      <div className={supervisionEnabled ? 'space-y-5' : 'space-y-5 opacity-65'}>
        <Panel title="时段" icon={Clock3} status={statusNode}>
          <FieldSet className="gap-0">
            <FieldGroup className={FIELD_GROUP_CLASS}>
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>时间规则</FieldTitle>
                </FieldContent>
                <Controller
                  control={form.control}
                  name="scheduleMode"
                  render={({ field }) => (
                    <Choice
                      label="时间规则"
                      value={field.value || 'allow'}
                      onChange={v => field.onChange(v)}
                      options={[
                        ['allow', '时段内答题解锁，时段外锁定'],
                        ['control', '时段内答题解锁，时段外自由看'],
                      ]}
                    />
                  )}
                />
              </Field>
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>全天管控</FieldTitle>
                  <FieldDescription className="text-xs leading-5">
                    开启后全天按时间规则执行；关闭后可在下方每周时段表自定义
                  </FieldDescription>
                </FieldContent>
                {toggle('allDay')}
              </Field>
              {!c.allDay && (
                <WeekSchedule
                  value={c.weekSlots}
                  onChange={v => change('weekSlots', v)}
                  holidayMode={c.holidayMode || 'sunday'}
                  onHolidayMode={v => change('holidayMode', v)}
                  scheduleMode={c.scheduleMode || 'allow'}
                />
              )}
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>强制休息</FieldTitle>
                  <FieldDescription className="text-xs leading-5">
                    关屏、答题不计时，重启不能跳过
                  </FieldDescription>
                </FieldContent>
                {toggle('restEnabled')}
              </Field>
              {c.restEnabled && (
                <>
                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>累计观看（分钟）</FieldTitle>
                    </FieldContent>
                    {number('restWatchMinutes', 1, 480)}
                  </Field>
                  <Field orientation="responsive" className="py-3.5">
                    <FieldContent>
                      <FieldTitle>强制休息（分钟）</FieldTitle>
                    </FieldContent>
                    {number('restMinutes', 1, 120)}
                  </Field>
                </>
              )}
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>管控期间不允许进系统设置与应用管理</FieldTitle>
                  <FieldDescription className="text-xs leading-5">
                    开启后（默认开启），管控、可用时长、临时解锁及自由时段均拦截系统设置与应用管理，防止从设置卸载；家长可在拦截页输管理密码临时进入 2 分钟。需开启无障碍或使用情况访问权限
                  </FieldDescription>
                </FieldContent>
                {toggle('blockSettingsDuringControl')}
              </Field>
              <Field orientation="responsive" className="py-3.5">
                <FieldContent>
                  <FieldTitle>答题页输密码解锁</FieldTitle>
                  <FieldDescription className="text-xs leading-5">
                    锁定页显示「家长验证解锁」，输对管理密码可临时解锁 30 分钟；默认关闭
                  </FieldDescription>
                </FieldContent>
                {toggle('allowQuizUnlock')}
              </Field>
              <Field className="py-3.5">
                <FieldContent>
                  <FieldTitle>不计时的应用</FieldTitle>
                  <FieldDescription className="text-xs leading-5">
                    这些应用在前台时不扣观看时长、不弹题，适合系统屏保、网课 App。最多 100 个。
                  </FieldDescription>
                  {!!(c.watchWhitelist && c.watchWhitelist.length > 0) &&
                    deviceData?.monitor &&
                    !deviceData.monitor.accessibility &&
                    !deviceData.monitor.usage && (
                      <p className="mt-1.5 text-xs leading-5 text-amber-600 dark:text-amber-400">
                        ⚠ 当前设备未开启「使用情况访问」和「无障碍服务」，系统无法识别正在运行的应用，白名单暂不会生效；请到「设置 → 设备」开启其中任意一项。
                      </p>
                    )}
                </FieldContent>
                <Controller
                  name="watchWhitelist"
                  control={form.control}
                  render={({ field }) => (
                    <WatchWhitelistField value={field.value || []} onChange={field.onChange} />
                  )}
                />
              </Field>
            </FieldGroup>
            <details className="group border-t border-border/60">
              <summary className="flex cursor-pointer list-none items-center gap-1.5 py-3.5 text-xs text-muted-foreground transition-colors hover:text-foreground [&::-webkit-details-marker]:hidden">
                <ChevronDown className="size-3 transition-transform group-open:rotate-180" />
                高级排障选项（流量判断、防遮挡、时区等）
              </summary>
              <FieldGroup className={FIELD_GROUP_CLASS}>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>按流量判断是否在播放</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      有的电视识别不到正在播放的视频。打开后，只要有声音、同时网络有明显下载，就当作在看视频
                    </FieldDescription>
                  </FieldContent>
                  {toggle('trafficAssistEnabled')}
                </Field>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>保留后台空窗口</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      部分小米电视会清理后台窗口，导致计时中断。计时不稳定时可以关掉它试试
                    </FieldDescription>
                  </FieldContent>
                  {toggle('backgroundAnchorEnabled')}
                </Field>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>强制使用全屏学习页</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      适合悬浮窗被视频遮挡的电视（如海信 VIDAA）
                    </FieldDescription>
                  </FieldContent>
                  {toggle('forceFullscreenGate')}
                </Field>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>时区</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      可用时段、全天可用和「今天」的重置都按这个时区算。
                      {!c.timezone && deviceData?.timezone
                        ? `设备当前：${deviceData.timezone}`
                        : '人在国外或设备时间固定在国内时区时改这里。'}
                    </FieldDescription>
                  </FieldContent>
                  <Choice
                    value={c.timezone || 'device'}
                    onChange={v => change('timezone', v === 'device' ? '' : v)}
                    options={zoneChoices}
                    label="时区"
                  />
                </Field>
              </FieldGroup>
            </details>
          </FieldSet>
        </Panel>
        <Panel
          title="网课时段与大人豁免"
          icon={Clock3}
          description="可跨午夜；优先级：解锁/暂停 > 大人豁免 > 网课 > 可用时段"
        >
          <TimePoints
            points={c.timePoints}
            exempt={c.exemptWindows}
            onPoints={v => change('timePoints', v)}
            onExempt={v => change('exemptWindows', v)}
          />
        </Panel>
      </div>
    </>
  );
}
