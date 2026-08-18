import type {JSX, Setter, ValidComponent} from 'solid-js';
import { createResource, createSignal, Show, ErrorBoundary, createContext } from 'solid-js';
import { Dropdown } from './Dropdown';
import * as api from './api';
import { OuterBox } from './boxes';
import { Dynamic } from 'solid-js/web';
import {DROPDOWN, Icon} from "./icons";

export class AppInternalEntry {
    constructor(
        public dropdownValue: JSX.Element,
        public name: () => JSX.Element,
        public value: () => JSX.Element,
        public visible?: (context: AppContext) => boolean,
    ) {}
}

export class AppExternalEntry {
    constructor(
        public dropdownValue: JSX.Element,
        public action: () => Promise<void>,
        public visible?: (context: AppContext) => boolean,
    ) {}
}

export type AppEntry = AppInternalEntry | AppExternalEntry

export interface AppContext {
    identity: api.User
    capabilities: api.UserCapabilities,
    modalDialogContents: Setter<ValidComponent | undefined>,
    modalDialog: HTMLDialogElement
}
export const AppContext = createContext<AppContext>();

export function App(props: { entries: AppEntry[] }) {
    const [ page, setPage ] = createSignal(0);
    const [identity] = createResource(async () => {
        return await api.fetchJSON('/dashboard/api/whoami', api.User);
    })
    const [capabilities] = createResource(async () => {
        return await api.fetchJSON('/dashboard/api/whatcanido', api.UserCapabilities);
    })

    const [modalDialogContents, setModalDialogContents] = createSignal<ValidComponent | undefined>();

    let modalDialog!: HTMLDialogElement;

    return <>
        <dialog ref={modalDialog} class="bg-transparent open:m-auto backdrop:bg-[rgba(0,0,0,0.4)]">
            <Dynamic component={modalDialogContents()}/>
        </dialog>
        <div class="bg-slate-200 text-slate-900 w-100% h-100% min-h-dvh">
        <div class="w-full h-full flex justify-center">
        <div class="w-2/3 h-full flex flex-col gap-5 p-5">
            <ErrorBoundary fallback={(error) => {
                console.error(error);
                return (<OuterBox>
                    <div class="bg-red-300 text-red-800 p-5 rounded-md">
                        Error: {error.message}
                    </div>
                </OuterBox>)
            }}>
            <Show when={identity() && capabilities()}><AppContext.Provider value={{
                identity: identity()!,
                capabilities: capabilities()!,
                modalDialogContents: setModalDialogContents,
                modalDialog: modalDialog
            }}>
            <div class="w-full flex flex-row gap-5 items-center">
                <div class="text-5xl">
                    <Dynamic component={(props.entries[page()] as AppInternalEntry).name} />
                </div>
                <div class="flex-1"></div>
                <Dropdown classes="py-2 px-3 rounded-md bg-white font-semibold shadow-sm hover:bg-slate-150" entries={props.entries.map((entry, index) => {
                    return {
                        value: entry.dropdownValue,
                        action: async () => {
                            if (entry instanceof AppInternalEntry) {
                                setPage(index);
                            } else {
                                await entry.action();
                            }
                        },
                        visible: (entry.visible ?? ((_) => true))({
                            identity: identity()!,
                            capabilities: capabilities()!,
                            modalDialogContents: setModalDialogContents,
                            modalDialog: modalDialog
                        }),
                    };
                })}>
                    {identity()!.email}
                    <Icon icon={DROPDOWN} class="-mr-1 size-6 text-slate-600"/>
                </Dropdown>
            </div>
            <div class="w-full h-0 border-b-2"></div>
            <Dynamic component={(props.entries[page()] as AppInternalEntry).value} />
            </AppContext.Provider></Show>
            </ErrorBoundary>
        </div>
        </div>
        </div>
    </>;
}
