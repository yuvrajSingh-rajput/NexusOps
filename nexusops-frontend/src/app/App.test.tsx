import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { App } from './App';

describe('App Component', () => {
  it('renders NexusOps brand header and navigation', () => {
    render(<App />);
    expect(screen.getByText('NexusOps')).toBeInTheDocument();
    expect(screen.getByText('Operations Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Active Requests')).toBeInTheDocument();
  });
});
