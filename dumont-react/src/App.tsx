import { Route, Routes } from "react-router-dom";
import DumontRoutes from "./DumontRoutes";

function App() {
  return (
    <Routes>
      <Route path="/*" element={<DumontRoutes />} />
    </Routes>
  );
}

export default App;
