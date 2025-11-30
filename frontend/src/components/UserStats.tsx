import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './UserStats.css';
import { UserCountResponse } from '../types/api';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

interface UserStatsProps {
  refreshTrigger: number;
}

function UserStats({ refreshTrigger }: UserStatsProps) {
  const [userCount, setUserCount] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchUserCount();
  }, [refreshTrigger]);

  const fetchUserCount = async (): Promise<void> => {
    try {
      setLoading(true);
      const response = await axios.get<UserCountResponse>(
        `${API_BASE_URL}/api/files/users/count`
      );
      setUserCount(response.data.count || 0);
      setError(null);
    } catch (err) {
      setError('Failed to fetch user count');
      console.error('Error fetching user count:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2>Import Statistics</h2>
      <div className="stats-content">
        {loading ? (
          <div className="loading">Loading...</div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : (
          <div className="stat-item">
            <span className="stat-label">Total Users Imported:</span>
            <span className="stat-value">{userCount}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default UserStats;

