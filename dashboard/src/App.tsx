import type { JSX, Resource } from 'solid-js';
import type { DropdownEntry } from './Dropdown';
import { createResource, createSignal, Show, ErrorBoundary, createContext } from 'solid-js';
import { Dropdown } from './Dropdown';
import * as api from './api';
import { OuterBox } from './boxes';
import { Dynamic } from 'solid-js/web';

export class AppInternalEntry {
    constructor(
        public dropdownValue: JSX.Element,
        public name: () => JSX.Element,
        public value: () => JSX.Element
    ) {}
}

export class AppExternalEntry {
    constructor(
        public dropdownValue: JSX.Element,
        public action: () => Promise<void>
    ) {}
}

export type AppEntry = AppInternalEntry | AppExternalEntry

export interface AppContext {
    identity: api.User
}
export const AppContext = createContext<AppContext>();

export function App(props: { entries: AppEntry[] }) {
    const [ page, setPage ] = createSignal(0);
    const dropdownEntries: DropdownEntry[] = props.entries.map((entry, index) => {
        return {
            value: entry.dropdownValue,
            action: async () => {
                if (entry instanceof AppInternalEntry) {
                    setPage(index);
                } else {
                    await entry.action();
                }
            },
        };
    });
    const [identity] = createResource(async () => {
        return await api.fetchJSON('/dashboard/api/whoami', api.User);
    })
    return (
        <div class="bg-slate-200 text-slate-900 w-100% h-100% min-h-dvh">
        <div class="w-full h-full flex justify-center">
        <div class="w-2/3 h-full flex flex-col gap-5 p-5">
            <ErrorBoundary fallback={(error) => {
                console.error(error);
                return (<OuterBox>
                    <div class="text-red-600 p-5">
                        Error: {error.message}
                    </div>
                </OuterBox>)
            }}>
            <Show when={identity()}><AppContext.Provider value={{
                identity: identity()!
            }}>
            <div class="w-full flex flex-row gap-5 items-center">
                <div class="text-5xl">
                    <Dynamic component={(props.entries[page()] as AppInternalEntry).name} />
                </div>
                <div class="flex-1"></div>
                <Dropdown classes="py-2 px-3 rounded-md bg-white font-semibold border-1 hover:bg-slate-150" entries={dropdownEntries}>
                    {identity()!.email}
                    <svg class="-mr-1 size-6 text-slate-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" data-slot="icon">
                    <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
                    </svg>
                </Dropdown>
            </div>
            <div class="w-full h-0 border-b-2"></div>
            <Dynamic component={(props.entries[page()] as AppInternalEntry).value} />
            </AppContext.Provider></Show>
            </ErrorBoundary>
        </div>
        </div>
        </div>
    );
};
