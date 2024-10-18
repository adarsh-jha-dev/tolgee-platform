import { Box, styled } from '@mui/material';
import { TranslationProviderType } from './translationProviders';
import { ProviderDescription } from './ProviderDescription';
import clsx from 'clsx';

const StyledContainer = styled(Box)`
  border: 1px solid ${({ theme }) => theme.palette.tokens.border.soft};
  gap: 20px;
  display: grid;
  border-radius: 16px;
  background: ${({ theme }) => theme.palette.tokens.background['paper-2']};
  padding: 20px;
  cursor: pointer;
  transition: box-shadow ease-in-out 0.2s, border-color ease-in-out 0.2s;
  &.selected {
    border-color: ${({ theme }) => theme.palette.primary.main};
    cursor: unset;
    box-shadow: 0px 0px 17px 0px
      ${({ theme }) => theme.palette.primary.main + '55'};
  }
`;

const StyledServices = styled(Box)`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
`;

const StyledDescription = styled(Box)`
  background: ${({ theme }) => theme.palette.tokens.background['paper-3']};
  padding: 0px 16px;
  border-radius: 16px;
`;

type Props = {
  provider: TranslationProviderType;
  selected: boolean;
  onSelect: (id: number) => void;
};

export const TranslationProvider = ({
  provider,
  selected,
  onSelect,
}: Props) => {
  const url = provider.url ? new URL(provider.url) : undefined;
  return (
    <StyledContainer
      className={clsx({ selected })}
      onClick={() => onSelect(provider.id)}
    >
      <Box
        display="flex"
        justifyContent="space-between"
        flexWrap="wrap"
        alignItems="start"
      >
        <img src={provider.logo} />
        <Box display="flex" alignItems="center" gap="20px">
          {url && (
            <a target="_blank" href={url.toString()} rel="noreferrer">
              {url.host}
            </a>
          )}
        </Box>
      </Box>
      {provider.services && (
        <StyledServices>
          {provider.services.map((item) => (
            <Box key={item}>{item}</Box>
          ))}
        </StyledServices>
      )}
      {provider.description && (
        <StyledDescription>
          <ProviderDescription description={provider.description} />
        </StyledDescription>
      )}
    </StyledContainer>
  );
};
