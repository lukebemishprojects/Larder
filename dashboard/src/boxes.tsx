import type { JSX, } from 'solid-js';

export function OuterBox(props: { children: JSX.Element }) {
    return (
        <div class="bg-slate-300 shadow-sm rounded-lg p-0 block flex flex-col">
            {props.children}
        </div>
    )
}