import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { RecorderPage } from './pages/RecorderPage';
import { WorkflowsPage } from './pages/WorkflowsPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/workflows" replace />} />
        <Route path="/recorder" element={<RecorderPage />} />
        <Route path="/workflows" element={<WorkflowsPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
