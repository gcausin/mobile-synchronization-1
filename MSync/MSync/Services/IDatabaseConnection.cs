using SQLite;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public interface IDatabaseConnection
    {
        SQLiteConnection Connection { get; set; }
        string Version { get; set; }
    }
}
