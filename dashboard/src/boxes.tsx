import {Accessor, createSignal, For, Show, type JSX, Setter, mergeProps,} from 'solid-js';
import {COPY, REMOVE, Icon} from "./icons";

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

export function BoxWithPartialHeader(props: { children: [(toggle: () => void) => JSX.Element, JSX.Element] }) {
    const [boxOpen, setBoxOpen] = createSignal(false);
    return (
        <OuterBox>
            {props.children[0](() => setBoxOpen(!boxOpen()))}
            <Show when={boxOpen()}>
                <div class="py-2.5 flex flex-col gap-2">
                    {props.children[1]}
                </div>
            </Show>
        </OuterBox>
    )
}

export function BoxWithHeader(props: { children: [JSX.Element, JSX.Element] }) {
    return <BoxWithPartialHeader>
        {(toggle) => <button class="cursor-pointer" onclick={toggle}>
            {props.children[0]}
        </button>}
        {props.children[1]}
    </BoxWithPartialHeader>
}

export function RowOf(props: JSX.HTMLAttributes<HTMLDivElement> & { children: JSX.Element }) {
    return <div {...{
        ...props,
        children: undefined,
        class: "flex flex-row items-stretch gap-0 [:where(&>*)]:bg-white [:where(&>*)]:shadow-sm [:where(&>*)]:p-2.5 [:where(&>*):first-child]:w-full [:where(&>*):first-child]:rounded-l-md [:where(&>*):last-child]:rounded-r-md [:where(&>*)]:rounded-none [:where(&>*)]:border-l-slate-300 [:where(&>*)]:border-l-1 [:where(&>*):first-child]:border-l-0 " + (props.class ?? "")
    }}>
        {props.children}
    </div>
}

export function TextInput(props: { type: string, placeholder: string, value: string, onchange: (value: string) => void, input?: JSX.InputHTMLAttributes<HTMLInputElement>, onsubmit?: (target: HTMLInputElement) => Promise<void> | void, allowenter?: boolean }) {
    return <input type={props.type} class="outline-none text-sm focus:inset-ring-blue-500 focus:ring-0 focus:outline-none focus:inset-ring-2" placeholder={props.placeholder} value={props.value} oninput={(e) => {
        props.onchange(e.target.value)
    }} onkeydown={async (e) => {
        if ('onsubmit' in props && (props.allowenter ?? true) && e.key == 'Enter') {
            await props.onsubmit!(e.currentTarget);
        }
    }} {...props.input ?? {}} />;
}

export function TextInputRow(props: { type: string, placeholder: string, value: string, onchange: (value: string) => void, input?: JSX.InputHTMLAttributes<HTMLInputElement>, onsubmit?: (target: HTMLInputElement) => Promise<void> | void, allowenter?: boolean }) {
    return <RowOf>
        <TextInput {...props}/>
    </RowOf>
}

export function TextList(props: { entries: Accessor<string[]>, setentries?: Setter<string[]> }) {
    const mutable = !(props.setentries === undefined);
    const [toAdd, setToAdd] = createSignal("");

    return <div class={`flex flex-row items-center gap-2 ${mutable ? "bg-white" : "bg-slate-150 text-slate-600"} text-sm focus-within:inset-ring-blue-500 focus-within:ring-0 focus-within:outline-none focus-within:shadow-none focus-within:inset-ring-2`}>
        <For each={props.entries()}>{(item, index) => (<>
            <div class="rounded-lg bg-slate-600 text-white px-2 flex flex-row gap-2">
                <div>{item}</div>
                {mutable ? <button class="cursor-pointer" onclick={() => {
                    const entries = [ ...props.entries() ];
                    entries.splice(index(), 1);
                    props.setentries!(entries);
                }}>
                    <Icon class="size-3" icon={REMOVE}/>
                </button> : <></>}
            </div>
        </>)}</For>
        {mutable ? <input class="focus:outline-none focus:shadow-none focus:ring-0 w-full flex-1"
                          type="text" value={toAdd()} oninput={(e) => {
            setToAdd(e.target.value);
        }} onkeydown={async (e) => {
            if (e.key == 'Enter') {
                const entry = toAdd();
                props.setentries!([ ...props.entries() ].concat([entry]));
                setToAdd("");
            }
        }}
        /> : <></>}
    </div>;
}

export function TextListRow(props: Parameters<typeof TextList>[0]) {
    return <RowOf>
        <TextList {...props}/>
    </RowOf>
}

export function TextCopy(props: { children?: JSX.Element, text: string }) {
    const [showCopied, setShowCopied] = createSignal(false);

    return <button class="text-sm bg-slate-150 py-0 w-full text-slate-600 cursor-pointer" onclick={async () => {
        await navigator.clipboard.writeText(props.text);
        setShowCopied(true);
    }}>
        <div class="flex flex-row items-center gap-2">
            {props.children ?? <div class="overflow-auto text-nowrap py-2.5">{props.text}</div>}
            <div class="flex-1"></div>
            <Show when={showCopied()}>
                <div class="py-2.5">Copied!</div>
            </Show>
            <Icon class="size-5 py-2.5" icon={COPY}/>
        </div>
    </button>;
}

export function TextCopyRow(props: Parameters<typeof TextCopy>[0]) {
    return <RowOf>
        <TextCopy {...props}/>
    </RowOf>
}

export function TextInputGroup(props: { type: string, placeholder: string, accessor?: Accessor<string>, setter?: (value: string) => void, input?: JSX.InputHTMLAttributes<HTMLInputElement> } & ({ units: JSX.Element } | { submit: JSX.Element, onsubmit: (target: HTMLInputElement) => Promise<void> | void, allowenter?: boolean })) {
    let reference!: HTMLInputElement;
    return <RowOf>
        <TextInput type={props.type} placeholder={props.placeholder} value={props.accessor?.() ?? ""} onchange={props.setter ?? (() => {})} onsubmit={'onsubmit' in props ? props.onsubmit : undefined} allowenter={'onsubmit' in props ? props.allowenter : undefined} input={
            mergeProps(props.input ?? {}, {
                ref: reference
            })
        }/>
        {'units' in props ? <div class="bg-slate-150 text-sm">{props.units}</div> :
            <button class="font-semibold bg-white text-sm cursor-pointer hover:bg-slate-200" onclick={async () => await props.onsubmit(reference)}>{props.submit}</button>}
    </RowOf>
}

export function Button(props: { children: JSX.Element, disabled?: boolean, onclick?: () => Promise<void> | void, class?: string }) {
    return (<button class={"font-semibold bg-white rounded-md text-sm shadow-sm py-2.5 px-3 block cursor-pointer hover:bg-slate-200 disabled:text-slate-400 disabled:bg-slate-150 disabled:cursor-auto " + (props.class ?? "")}
        disabled={props.disabled} onclick={props.onclick}>
        {props.children}
    </button>)
}
