import type { JSX } from 'solid-js';
import type { DropdownEntry } from './Dropdown';
import { Dropdown } from './Dropdown';

export class AppInternalEntry {
    constructor(
        public dropdownValue: JSX.Element,
        public name: string,
        public value: JSX.Element
    ) {}
}

export class AppExternalEntry {
    constructor(
        public dropdownValue: JSX.Element
    ) {}
}

export type AppEntry = AppInternalEntry | AppExternalEntry

export function App(props: { entries: AppEntry[] }) {
    const dropdownEntries: DropdownEntry[] = props.entries.map((entry) => {
        return {
            value: entry.dropdownValue
        };
    });
    return (
        <div class="bg-slate-200 text-slate-900 w-dvw h-dvh">
        <div class="w-full h-full flex justify-center">
        <div class="w-2/3 h-full flex flex-col gap-5 p-5">
            <div class="w-full flex flex-row gap-5 items-center">
                <div class="text-5xl">Page name</div>
                <div class="flex-1"></div>
                <Dropdown classes="py-2 px-3 rounded-md bg-white font-semibold border-1 hover:bg-slate-150" entries={dropdownEntries}>
                    {/* TODO: email */}
                    email@here
                    <svg class="-mr-1 size-6 text-slate-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" data-slot="icon">
                    <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
                    </svg>
                </Dropdown>
            </div>
            <div class="w-full h-0 border-b-2"></div>
        </div>
        </div>
        </div>
    );
};

