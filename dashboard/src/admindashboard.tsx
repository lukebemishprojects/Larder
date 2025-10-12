/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';

const root = document.getElementById('root');

render(() => <App entries={[
    new AppExternalEntry(<>Users</>),
    new AppExternalEntry(<>Repositories</>),
    new AppExternalEntry(<>Browse</>),
    new AppExternalEntry(<>Dashboard</>),
    new AppExternalEntry(<>Sign Out</>)
]}/>, root!);
