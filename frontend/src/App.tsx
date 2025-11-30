import React, { useState } from 'react';
import './App.css';
import FileUpload from './components/FileUpload';
import UserStats from './components/UserStats';

function App() {
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0);

  const handleUploadSuccess = (): void => {
    setTimeout(() => {
      setRefreshTrigger(prev => prev + 1);
    }, 2000);
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>Bulk User Import System</h1>
        <p>Upload a CSV file to import users in bulk</p>
      </header>
      <main className="App-main">
        <FileUpload onUploadSuccess={handleUploadSuccess} />
        <UserStats refreshTrigger={refreshTrigger} />
      </main>
    </div>
  );
}

export default App;

