import { Tabs } from 'antd';
import type { LucideIcon } from 'lucide-react';

export type AssetNavigationTabItem<T extends string = string> = {
  key: T;
  label: string;
  icon: LucideIcon;
  enabled: boolean;
};

type AssetNavigationTabsProps<T extends string> = {
  activeKey: T;
  ariaLabel: string;
  onSelectTab: (tabKey: T) => void;
  tabs: readonly AssetNavigationTabItem<T>[];
};

export function AssetNavigationTabs<T extends string>(props: AssetNavigationTabsProps<T>) {
  return (
    <Tabs
      activeKey={props.activeKey}
      aria-label={props.ariaLabel}
      className="asset-navigation-tabs"
      items={props.tabs.map((tab) => {
        const Icon = tab.icon;
        return {
          disabled: !tab.enabled,
          key: tab.key,
          label: (
            <span className="asset-navigation-tab-label" title={tab.label}>
              <Icon size={15} />
              <span>{tab.label}</span>
            </span>
          )
        };
      })}
      size="middle"
      onChange={(key) => props.onSelectTab(key as T)}
    />
  );
}
