import { Accessor, createSignal, JSX, Setter, Show } from 'solid-js';

export type OrError = { status: "ok" } | { status: "error", err: any };

export function orErrorSignal() {
    return createSignal<OrError>({ status: "ok" });
}
export function OrError(props: { get: Accessor<OrError> }) {
    return <Show when={props.get().status == "error"}>
        <div class="text-red-500 text-xs px-1">{(props.get() as { err:any }).err}</div>
    </Show>
}

