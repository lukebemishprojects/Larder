/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';

const root = document.getElementById('root');

render(() => <App entries={[
    new AppExternalEntry(<>Account information</>),
    new AppExternalEntry(<>Namespaces</>),
    new AppExternalEntry(<>Deployments</>),
    new AppExternalEntry(<>Browse</>),
    new AppExternalEntry(<div class="flex flex-row">
        <div>
        Admin Dashboard
        </div>
        <div class="flex-1"></div>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="size-5 text-slate-600">
            <path fill-rule="evenodd" d="M10 1a4.5 4.5 0 0 0-4.5 4.5V9H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-.5V5.5A4.5 4.5 0 0 0 10 1Zm3 8V5.5a3 3 0 1 0-6 0V9h6Z" clip-rule="evenodd" />
        </svg>
    </div>),
    new AppExternalEntry(<>Sign Out</>)
]}/>, root!);
