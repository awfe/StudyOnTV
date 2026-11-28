import type { ReactNode } from 'react';
import { Controller, type UseFormReturn } from 'react-hook-form';
import { Repeat2 } from 'lucide-react';
import { ClearExtraPoolButton } from '@/components/ClearExtraPoolButton';
import { Field, FieldContent, FieldDescription, FieldGroup, FieldSet, FieldTitle } from '@/components/ui/field';
import type { Rules } from '@/schema';
import { Choice, FIELD_GROUP_CLASS, Panel } from './RulePrimitives';

type Props = {
  form: UseFormReturn<Rules>;
  c: Rules;
  state: any;
  sessionId: number;
  statusNode?: ReactNode;
  toggle: (key: any) => ReactNode;
  number: (key: any, min: number, max: number) => ReactNode;
};

export default function MemoryRulesTab({
  form,
  c,
  state,
  sessionId,
  statusNode,
  toggle,
  number,
}: Props) {
  return (
    <>
      <Panel title="科学记忆与错题加练" icon={Repeat2} status={statusNode}>
        <FieldSet className="gap-0">
          <FieldGroup className={FIELD_GROUP_CLASS}>
            <Field orientation="responsive" className="py-3.5">
              <FieldContent>
                <FieldTitle>先背再测</FieldTitle>
                <FieldDescription className="text-xs leading-5">
                  做题前先展示答案与解析卡片背诵一遍，再打乱顺序小测检验，大幅提升初次正确率
                </FieldDescription>
              </FieldContent>
              {toggle('memorizeFirst')}
            </Field>
            <Field orientation="responsive" className="py-3.5">
              <FieldContent>
                <FieldTitle>按记忆曲线复习（Anki 间隔重复）</FieldTitle>
                <FieldDescription className="text-xs leading-5">
                  到遗忘临界点自动优先安排复习，答对越稳下次复习间隔越长
                </FieldDescription>
              </FieldContent>
              {toggle('spacedRepetitionEnabled')}
            </Field>
            {c.spacedRepetitionEnabled && (
              <>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>间隔重复范围</FieldTitle>
                    <FieldDescription className="text-xs leading-5">
                      「只背做错过的题」适合题库量大、只想专注清零错题本的场景
                    </FieldDescription>
                  </FieldContent>
                  <Controller
                    name="spacedScope"
                    control={form.control}
                    render={({ field }) => (
                      <Choice
                        label="间隔重复范围"
                        value={field.value || 'all'}
                        onChange={field.onChange}
                        options={[
                          ['all', '全部启用题目'],
                          ['wrong_only', '只背做错过的题（错题艾宾浩斯）'],
                        ]}
                      />
                    )}
                  />
                </Field>
                <Field className="py-3.5">
                  <FieldDescription>
                    已纳入记忆计划 {state?.repetition?.studied || 0} 题，今日到期复习 {state?.repetition?.due || 0} 题；进度跨重启保留。
                  </FieldDescription>
                </Field>
              </>
            )}
            <Field orientation="responsive" className="py-3.5">
              <FieldContent>
                <FieldTitle>答错累计加练</FieldTitle>
              </FieldContent>
              {toggle('extraEnabled')}
            </Field>
            {c.extraEnabled && (
              <>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>累计答错（题）</FieldTitle>
                  </FieldContent>
                  {number('wrongThreshold', 1, 100)}
                </Field>
                <Field orientation="responsive" className="py-3.5">
                  <FieldContent>
                    <FieldTitle>每次增加加练（题）</FieldTitle>
                  </FieldContent>
                  {number('extraQuestions', 1, 100)}
                </Field>
              </>
            )}
            <Field orientation="responsive" className="py-3.5">
              <FieldContent>
                <FieldTitle>当前加练池</FieldTitle>
                <FieldDescription className="text-xs leading-5">
                  当前待完成加练 {Number(state?.learning?.extraPool) || 0} 题；积压过多时可一键清空，不会删除原题库与错题记录
                </FieldDescription>
              </FieldContent>
              <ClearExtraPoolButton sessionId={sessionId} count={Number(state?.learning?.extraPool) || 0} />
            </Field>
          </FieldGroup>
        </FieldSet>
      </Panel>
      <p className="text-xs leading-5 text-muted-foreground">
        朗读怎么读、读题目还是连同选项和答案一起读，已经移到
        <a className="mx-1 underline underline-offset-4" href="#/settings/speech">
          设置 → 朗读
        </a>
        ，那里还顺着其它设备设置，找起来更顺手。
      </p>
    </>
  );
}
