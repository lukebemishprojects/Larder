import {JSX} from "solid-js";

export interface IconType {
    svgpath: () => JSX.Element,
    viewBox?: string
}

export function Icon(props: Omit<{ icon: IconType } & JSX.SvgSVGAttributes<SVGSVGElement>, "children" | "fill" | "xmlns" | "viewBox">) {
    return <svg {...{
        ...props,
        children: undefined,
        icon: undefined,
        xmlns: "http://www.w3.org/2000/svg",
        viewBox: props.icon.viewBox ?? "0 0 20 20",
        fill: "currentColor"
    }}>
        {props.icon.svgpath()}
    </svg>
}

export const DROPDOWN: IconType = {
    svgpath: () => <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
}

export const DOTDOTDOT: IconType = {
    svgpath: () => <path d="M3 10a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM8.5 10a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM15.5 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z" />
}

export const COPY: IconType = {
    svgpath: () => <>
        // Font Awesome Free v7.3.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free Copyright 2026 Fonticons, Inc.
        <path d="M384 336l-192 0c-8.8 0-16-7.2-16-16l0-256c0-8.8 7.2-16 16-16l133.5 0c4.2 0 8.3 1.7 11.3 4.7l58.5 58.5c3 3 4.7 7.1 4.7 11.3L400 320c0 8.8-7.2 16-16 16zM192 384l192 0c35.3 0 64-28.7 64-64l0-197.5c0-17-6.7-33.3-18.7-45.3L370.7 18.7C358.7 6.7 342.5 0 325.5 0L192 0c-35.3 0-64 28.7-64 64l0 256c0 35.3 28.7 64 64 64zM64 128c-35.3 0-64 28.7-64 64L0 448c0 35.3 28.7 64 64 64l192 0c35.3 0 64-28.7 64-64l0-16-48 0 0 16c0 8.8-7.2 16-16 16L64 464c-8.8 0-16-7.2-16-16l0-256c0-8.8 7.2-16 16-16l16 0 0-48-16 0z"/>
    </>,
    viewBox: "0 0 448 512"
}
