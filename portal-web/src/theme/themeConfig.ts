import type { ThemeConfig } from 'antd';

/**
 * 企业级浅色简约风 Design Token。
 * 参考飞书 / Notion 的干净界面：白色卡片、浅灰页面底、克制的阴影与边框。
 */
export const lightThemeConfig: ThemeConfig = {
  token: {
    colorPrimary: '#2f54eb',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#f5222d',
    colorInfo: '#2f54eb',
    colorTextBase: '#1d2129',
    colorBgLayout: '#f5f6f7',
    colorBgContainer: '#ffffff',
    colorBorder: '#e5e6eb',
    colorBorderSecondary: '#f0f0f1',
    borderRadius: 8,
    fontSize: 14,
    fontFamily:
      "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    boxShadowTertiary: '0 1px 2px rgba(29, 33, 41, 0.04)',
    controlHeight: 34,
    wireframe: false
  },
  components: {
    Layout: {
      siderBg: '#ffffff',
      headerBg: '#ffffff',
      headerHeight: 56,
      headerPadding: '0 24px',
      bodyBg: '#f5f6f7'
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: '#4e5969',
      itemHoverBg: '#f2f3f5',
      itemSelectedBg: '#e8f0ff',
      itemSelectedColor: '#2f54eb',
      itemBorderRadius: 6,
      itemMarginInline: 8,
      subMenuItemBg: 'transparent',
      groupTitleColor: '#86909c'
    },
    Card: {
      borderRadiusLG: 10,
      paddingLG: 20,
      boxShadowTertiary: 'none'
    },
    Table: {
      headerBg: '#fafbfc',
      headerColor: '#4e5969',
      rowHoverBg: '#f7f8fa',
      borderColor: '#f0f0f1'
    },
    Button: {
      borderRadius: 6,
      controlHeight: 34,
      primaryShadow: 'none'
    },
    Input: {
      borderRadius: 6
    },
    Modal: {
      borderRadiusLG: 10
    },
    Tabs: {
      itemSelectedColor: '#2f54eb',
      inkBarColor: '#2f54eb'
    },
    Breadcrumb: {
      itemColor: '#86909c',
      lastItemColor: '#1d2129',
      linkColor: '#4e5969'
    }
  }
};
