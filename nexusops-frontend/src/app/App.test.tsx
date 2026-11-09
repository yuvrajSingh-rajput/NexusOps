import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { createMemoryRouter } from 'react-router-dom';
import { App } from './App';
import { routes } from './routes';
import { useAuthStore } from '@/features/auth/authStore';

describe('NexusOps App Routing & Authentication', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({
      token: null,
      user: null,
      isAuthenticated: false,
    });
  });

  it('renders login screen when unauthenticated', async () => {
    const memoryRouter = createMemoryRouter(routes, { initialEntries: ['/'] });
    render(<App router={memoryRouter} />);

    expect(await screen.findByText('NexusOps')).toBeInTheDocument();
    expect(screen.getByText('Intelligent Enterprise Operations Portal')).toBeInTheDocument();
    expect(screen.getByText('Work Email')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign In/i })).toBeInTheDocument();
    expect(screen.getByText('Register Organization')).toBeInTheDocument();
  });

  it('renders enterprise operations dashboard when authenticated', async () => {
    localStorage.setItem('nexusops_access_token', 'mock-jwt-token');
    useAuthStore.setState({
      token: 'mock-jwt-token',
      isAuthenticated: true,
      user: {
        userId: '11111111-1111-1111-1111-111111111111',
        organizationId: '22222222-2222-2222-2222-222222222222',
        email: 'alex.rivera@acme.com',
        firstName: 'Alex',
        lastName: 'Rivera',
        jobTitle: 'Senior Software Engineer',
        roles: ['ROLE_EMPLOYEE'],
        permissions: ['REQUEST_CREATE'],
        availableOrganizations: [
          {
            organizationId: '22222222-2222-2222-2222-222222222222',
            organizationName: 'Acme Technologies',
            organizationSlug: 'acme-tech',
            isCurrent: true,
          },
        ],
      },
    });

    const memoryRouter = createMemoryRouter(routes, { initialEntries: ['/'] });
    render(<App router={memoryRouter} />);

    expect(await screen.findByText('Operations Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Active Requests')).toBeInTheDocument();
    expect(screen.getByText('Pending Approvals')).toBeInTheDocument();
    expect(screen.getByText('Departments')).toBeInTheDocument();
    expect(screen.getByText('Employees & Directory')).toBeInTheDocument();
    expect(screen.getAllByText('Acme Technologies').length).toBeGreaterThanOrEqual(1);
  });
});
