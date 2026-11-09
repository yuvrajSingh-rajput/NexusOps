import React from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { router } from './routes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

interface AppProps {
  router?: ReturnType<typeof import('react-router-dom').createBrowserRouter>;
}

export const App: React.FC<AppProps> = ({ router: customRouter }) => {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={customRouter || router} />
    </QueryClientProvider>
  );
};
