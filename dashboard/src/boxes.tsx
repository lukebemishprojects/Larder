import { Accessor, createSignal, For, Show, type JSX, } from 'solid-js';
import {COPY, Icon} from "./icons";

export function OuterBox(props: { children: JSX.Element }) {
    return (
        <div class="bg-slate-300 shadow-sm rounded-md p-0 flex flex-col">
            {props.children}
        </div>
    )
}

export function InnerHoverElements<T>(props: { basis: T[], foreach: (item: T) => JSX.Element }) {
    return <div class="flex flex-col">
        <For each={props.basis}>
            {(item) => <div class="px-2.5 py-1 hover:bg-slate-350">
                {props.foreach(item)}
            </div>}
        </For>
    </div>
}

export function InnerElement(props: { children: JSX.Element }) {
    return (
        <div class="px-2.5">{props.children}</div>
    )
}

export function BoxInside(props: { children: JSX.Element }) {
    return (<div class="py-2.5 flex flex-col gap-2">
        {props.children}
    </div>)
}

export function BoxWithHeader(props: { children: [JSX.Element, JSX.Element] }) {
    const [boxOpen, setBoxOpen] = createSignal(false);
    return (
        <OuterBox>
            <button class="bg-white shadow-sm rounded-md p-2.5 block cursor-pointer" onclick={() => setBoxOpen(!boxOpen())}>
                {props.children[0]}
            </button>
            <Show when={boxOpen()}>
            <div class="py-2.5 flex flex-col gap-2">
                {props.children[1]}
            </div>
            </Show>
        </OuterBox>
    )
}

export function TextInput(props: { type: string, placeholder: string, value: string, onchange: (value: string) => void, input?: JSX.InputHTMLAttributes<HTMLInputElement> }) {
    return (
        <input type={props.type} class="bg-white border-1 rounded-md p-2.5 text-sm focus:inset-ring-blue-500 focus:border-1 focus:ring-0 focus:outline-none focus:shadow-none focus:inset-ring-2 w-full" placeholder={props.placeholder} value={props.value} oninput={(e) => {
            props.onchange(e.target.value)
        }} {...props.input} />
    )
}

export function TextCopy(props: { children?: JSX.Element, text: string }) {
    const [showCopied, setShowCopied] = createSignal(false);

    return (
        <button class="border rounded-md p-2.5 text-sm bg-slate-150 w-full text-slate-600 cursor-pointer" onclick={(e) => {
            navigator.clipboard.writeText(props.text);
            setShowCopied(true);
        }}>
            <div class="flex flex-row items-center gap-2">
                {props.children ?? props.text}
                <div class="flex-1"></div>
                <Show when={showCopied()}>
                    Copied!
                </Show>
                <Icon class="size-5" icon={COPY}/>
            </div>
        </button>
    )
}

export function TextInputGroup(props: { type: string, placeholder: string, accessor?: Accessor<string>, setter?: (value: string) => void, input?: JSX.InputHTMLAttributes<HTMLInputElement> } & ({ units: JSX.Element } | { submit: JSX.Element, onsubmit: (target: HTMLInputElement) => Promise<void> | void, allowenter?: boolean })) {
    let reference!: HTMLInputElement;
    return (
        <div class="flex flex-row block w-full">
            <input ref={reference} type={props.type} class="bg-white border-1 rounded-md p-2.5 text-sm focus:inset-ring-blue-500 focus:border-1 focus:ring-0 focus:outline-none focus:shadow-none focus:inset-ring-2 flex-1 rounded-r-none" placeholder={props.placeholder} value={props.accessor?.() ?? ""} onkeydown={async (e) => {
                if ('submit' in props && (props.allowenter ?? true) && e.key == 'Enter') {
                    await props.onsubmit(e.currentTarget);
                }
            }} oninput={(e) => {
                props.setter?.(e.target.value)
            }} {...props.input}/>
            {'units' in props ? <div class="bg-slate-150 rounded-md border-1 border-l-0 p-2.5 block text-sm rounded-l-none">{props.units}</div> :
                <button class="font-semibold bg-white rounded-md border-1 border-l-0 p-2.5 block text-sm rounded-l-none cursor-pointer hover:bg-slate-200" onclick={async () => await props.onsubmit(reference)}>{props.submit}</button>}
        </div>
    )
}

export function Button(props: { children: JSX.Element, disabled?: boolean, onclick?: () => Promise<void> | void }) {
    return (<button class="font-semibold bg-white rounded-md text-sm border-1 py-2.5 px-3 block cursor-pointer hover:bg-slate-200 disabled:text-slate-400 disabled:bg-slate-150 disabled:cursor-auto"
        disabled={props.disabled} onclick={props.onclick}>
        {props.children}
    </button>)
}
