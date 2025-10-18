import { Accessor, createSignal, For, Setter, Show, type JSX, } from 'solid-js';

export function OuterBox(props: { children: JSX.Element }) {
    return (
        <div class="bg-slate-300 shadow-sm rounded-lg p-0 block flex flex-col">
            {props.children}
        </div>
    )
}

export function BoxHeader(props: { children: JSX.Element }) {
    return (
        <div class="bg-white shadow-sm rounded-lg p-2.5 block">
            {props.children}
        </div>
    )
}

export function InnerHoverElements<T>(props: { basis: T[], foreach: (item: T) => JSX.Element }) {
    return <div class="block flex flex-col">
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

export function BoxWithHeader(props: { children: [JSX.Element, JSX.Element] }) {
    const [boxOpen, setBoxOpen] = createSignal(false);
    return (
        <OuterBox>
            <BoxHeader>
                <button class="cursor-pointer w-full" onclick={() => setBoxOpen(!boxOpen())}>
                    {props.children[0]}
                </button>
            </BoxHeader>
            <Show when={boxOpen()}>
            <div class="py-2.5 block flex flex-col gap-2">
                {props.children[1]}
            </div>
            </Show>
        </OuterBox>
    )
}

export function TextInputGroup(props: { type: string, placeholder: string, accessor: Accessor<string>, setter: Setter<string> } & ({ units: JSX.Element } | { submit: JSX.Element, onsubmit: () => Promise<void> })) {
    return (
        <div class="flex flex-row block">
            <input type={props.type} class="bg-white border-1 rounded-lg p-2.5 text-sm bg-slate-150 focus:inset-ring-blue-500 focus:border-1 focus:ring-0 focus:outline-none focus:shadow-none focus:inset-ring-2 flex-1 rounded-r-none" placeholder={props.placeholder} value={props.accessor()} onkeydown={async (e) => {
                if ('submit' in props && e.key == 'Enter') {
                    await props.onsubmit();
                }
            }} oninput={(e) => {
                props.setter(e.target.value)
            }}/>
            {'units' in props ? <div class="bg-slate-150 rounded-lg border-1 border-l-0 p-2.5 block text-sm rounded-l-none">{props.units}</div> : <button class="font-semibold bg-white rounded-lg border-1 border-l-0 p-2.5 block text-sm rounded-l-none cursor-pointer bg-slate-150 hover:bg-slate-200" onclick={props.onsubmit}>{props.submit}</button>}
        </div>
    )
}
