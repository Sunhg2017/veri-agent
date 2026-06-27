import type { Decorator, Preview } from '@storybook/react-vite';
import '../src/styles.css';
import '../src/platform/i18n';

const withTheme: Decorator = (Story, context) => {
  const theme = context.globals.theme === 'dark' ? 'dark' : 'light';
  document.documentElement.dataset.theme = theme;
  document.documentElement.dataset.themeMode = theme;
  document.body.style.margin = '0';

  return (
    <div className="storybook-shell">
      <Story />
    </div>
  );
};

const preview: Preview = {
  decorators: [withTheme],
  globalTypes: {
    theme: {
      description: 'Portal visual theme',
      toolbar: {
        icon: 'mirror',
        items: [
          { value: 'light', title: 'Light' },
          { value: 'dark', title: 'Dark' }
        ],
        title: 'Theme'
      }
    }
  },
  initialGlobals: {
    theme: 'light'
  },
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i
      }
    },
    layout: 'fullscreen'
  }
};

export default preview;
