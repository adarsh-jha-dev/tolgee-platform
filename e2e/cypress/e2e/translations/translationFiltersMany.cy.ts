import { visitTranslations } from '../../common/translations';
import { gcy, selectInSelect } from '../../common/shared';
import { waitForGlobalLoading } from '../../common/loading';
import { translationsNsAndTagsTestData } from '../../common/apiCalls/testData/testData';
import { login } from '../../common/apiCalls/common';

describe('filter by namespaces and tags', () => {
  let projectId: number = null;

  before(() => {
    translationsNsAndTagsTestData.clean();
    translationsNsAndTagsTestData.generateStandard().then((r) => {
      projectId = r.body.projects[0].id;
    });
  });

  after(() => {
    translationsNsAndTagsTestData.clean();
  });

  beforeEach(() => {
    login('olin', 'admin');
    visit();
  });

  it('searches in tags', () => {
    selectInSelect(gcy('translations-filter-select'), 'Tags');
    gcy('search-select-search').type('Tag 15');
    expect(gcy('search-select-item').should('have.length', 1));
  });

  it('searches in namespaces', () => {
    selectInSelect(gcy('translations-filter-select'), 'Namespaces');
    gcy('search-select-search').type('Namespace 15');
    expect(gcy('search-select-item').should('have.length', 1));
  });

  it('filters by tag', () => {
    selectInSelect(gcy('translations-filter-select'), 'Tags');
    gcy('search-select-item').contains('Tag 02').click();
    waitForGlobalLoading();
    gcy('translations-key-count').contains('1').should('exist');
  });

  it('filters by namespace', () => {
    selectInSelect(gcy('translations-filter-select'), 'Namespaces');
    gcy('search-select-item').contains('Namespace 02').click();
    waitForGlobalLoading();
    gcy('translations-key-count').contains('1').should('exist');
  });

  const visit = () => {
    visitTranslations(projectId);
  };
});
