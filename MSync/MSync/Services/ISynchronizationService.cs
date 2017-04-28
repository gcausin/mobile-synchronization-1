using Model.Base;
using SQLite;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public class SynchronizationParameters
    {
        public List<string> DontDownload { get; set; } = new List<string>();
        public List<string> DontUpload { get; set; } = new List<string>();
        public string Username { get; set; }
        public string Password { get; set; }
        public int RecordsToDelete { get; set; }
        public int RecordsDeleted { get; set; }
        public string Server { get; set; }
        public int Downloaded { get; set; }
        public Action<Exception> ExceptionHandler { get; set; }
        public Action Refresh { get; set; }
        public int RecordsDeletedAtServer { get; set; }
        public List<EntitySync> EntitiesInSynchronization { get; set; }
        public int Uploaded { get; set; }
        public Action FinalAction { get; set; }
    }

    public interface ISynchronizationService
    {
        EntitySync ForEntity<T>(SynchronizationParameters synchronizationParameters) where T : AbstractEntity, new();
        void Synchronize(SynchronizationParameters synchronizationParameters);
        void CreateTables();
        void DropTables();
        IEnumerable<Type> AllEntities { get; }
        List<EntitySync> Entities { get; }
        void Reset();
    }
}
