import {
  Box,
  Button,
  Dialog,
  DialogTitle,
  Step,
  StepContent,
  StepLabel,
  Stepper,
  styled,
} from '@mui/material';
import { T, useTranslate } from '@tolgee/react';
import { Formik } from 'formik';
import { useState } from 'react';

import { Validation } from 'tg.constants/GlobalValidationSchema';
import { components } from 'tg.service/apiSchema.generated';
import { useApiMutation, useApiQuery } from 'tg.service/http/useQueryApi';
import { messageService } from 'tg.service/MessageService';
import LoadingButton from 'tg.component/common/form/LoadingButton';
import { FiltersType } from 'tg.component/translation/translationFilters/tools';
import { User } from 'tg.component/UserAccount';
import { TaskCreateForm } from 'tg.ee/task/components/taskCreate/TaskCreateForm';
import { TranslationStateType } from 'tg.ee/task/components/taskCreate/TranslationStateFilter';
import { translationProviders } from './translationProviders';
import { TranslationAgency } from './TranslationAgency';

type TaskType = components['schemas']['TaskModel']['type'];
type LanguageModel = components['schemas']['LanguageModel'];

const StyledMainTitle = styled(DialogTitle)`
  padding-bottom: 0px;
`;

const StyledSubtitle = styled('div')`
  padding: ${({ theme }) => theme.spacing(0, 3, 2, 3)};
  color: ${({ theme }) => theme.palette.text.secondary};
`;

const StyledContainer = styled('div')`
  display: grid;
  padding: ${({ theme }) => theme.spacing(3)};
  gap: ${({ theme }) => theme.spacing(0.5, 3)};
  padding-top: ${({ theme }) => theme.spacing(1)};
  width: min(calc(100vw - 64px), 1000px);
`;

const StyledActions = styled('div')`
  display: flex;
  gap: 8px;
  padding-top: 24px;
  justify-content: end;
`;

export type InitialValues = {
  type: TaskType;
  name: string;
  description: string;
  languages: number[];
  dueDate: number;
  languageAssignees: Record<number, User[]>;
  selection: number[];
};

type Props = {
  open: boolean;
  onClose: () => void;
  onFinished: () => void;
  projectId: number;
  allLanguages: LanguageModel[];
  initialValues?: Partial<InitialValues>;
};

export const OrderTranslationsDialog = ({
  open,
  onClose,
  onFinished,
  projectId,
  allLanguages,
  initialValues,
}: Props) => {
  const { t } = useTranslate();

  const createTasksLoadable = useApiMutation({
    url: '/v2/projects/{projectId}/tasks/create-multiple-tasks',
    method: 'post',
    invalidatePrefix: ['/v2/projects/{projectId}/tasks', '/v2/user-tasks'],
  });

  const [filters, setFilters] = useState<FiltersType>({});
  const [stateFilters, setStateFilters] = useState<TranslationStateType[]>([]);
  const [languages, setLanguages] = useState(initialValues?.languages ?? []);

  const selectedLoadable = useApiQuery({
    url: '/v2/projects/{projectId}/translations/select-all',
    method: 'get',
    path: { projectId },
    query: {
      ...filters,
      languages: allLanguages.map((l) => l.tag),
    },
    options: {
      enabled: !initialValues?.selection,
    },
  });

  const selectedKeys =
    initialValues?.selection ?? selectedLoadable.data?.ids ?? [];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xl">
      <StyledMainTitle>
        <T keyName="order_translations_title" />
      </StyledMainTitle>
      <StyledSubtitle>
        <T
          keyName="order_translations_keys_subtitle"
          params={{ value: selectedKeys.length }}
        />
      </StyledSubtitle>

      <Formik
        initialValues={{
          type: initialValues?.type ?? 'TRANSLATE',
          name: initialValues?.name ?? '',
          description: initialValues?.description ?? '',
          dueDate: initialValues?.dueDate ?? undefined,
          assignees: initialValues?.languageAssignees ?? {},
        }}
        validationSchema={Validation.CREATE_TASK_FORM(t)}
        onSubmit={async (values) => {
          const data = languages.map((languageId) => ({
            type: values.type,
            name: values.name,
            description: values.description,
            languageId: languageId,
            dueDate: values.dueDate,
            assignees: values.assignees[languageId]?.map((u) => u.id) ?? [],
            keys: selectedKeys,
          }));
          createTasksLoadable.mutate(
            {
              path: { projectId },
              query: {
                filterState: stateFilters.filter((i) => i !== 'OUTDATED'),
                filterOutdated: stateFilters.includes('OUTDATED'),
              },
              content: {
                'application/json': { tasks: data },
              },
            },
            {
              onSuccess() {
                messageService.success(
                  <T
                    keyName="order_translation_success_message"
                    params={{ count: languages.length }}
                  />
                );
                onFinished();
              },
            }
          );
        }}
      >
        {({ submitForm }) => {
          return (
            <StyledContainer>
              <Stepper orientation="vertical">
                <Step key={1}>
                  <StepLabel>
                    <T keyName="order_translation_choose_translation_agency_title" />
                  </StepLabel>
                  <StepContent>
                    <Box display="grid" gap="20px">
                      {translationProviders.map((provider, i) => (
                        <TranslationAgency key={i} provider={provider} />
                      ))}
                    </Box>
                  </StepContent>
                </Step>

                <Step key={2}>
                  <StepLabel>
                    <T keyName="order_translation_create_task_title" />
                  </StepLabel>
                  <StepContent>
                    <TaskCreateForm
                      selectedKeys={selectedKeys}
                      languages={languages}
                      setLanguages={setLanguages}
                      allLanguages={allLanguages}
                      filters={filters}
                      setFilters={
                        initialValues?.selection ? setFilters : undefined
                      }
                      stateFilters={stateFilters}
                      setStateFilters={setStateFilters}
                      projectId={projectId}
                      hideDueDate
                    />
                  </StepContent>
                </Step>
              </Stepper>
              <StyledActions>
                <Button onClick={onClose}>{t('global_cancel_button')}</Button>
                <LoadingButton
                  disabled={!languages.length}
                  onClick={submitForm}
                  color="primary"
                  variant="contained"
                  loading={createTasksLoadable.isLoading}
                  data-cy="order-translation-submit"
                >
                  {t('order_translation_submit_button')}
                </LoadingButton>
              </StyledActions>
            </StyledContainer>
          );
        }}
      </Formik>
    </Dialog>
  );
};
