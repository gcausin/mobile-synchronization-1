using System;
using SQLite;

namespace MobileSyncModels.Model.System
{
    public class LastEntitySyncTime
    {
        private string pk_;
        [PrimaryKey, MaxLength(36)]
        public string Pk { get { return pk_ ?? (pk_ = Guid.NewGuid().ToString()); } set { pk_ = value; } }
        [NotNull, Unique]
        public string EntityName { get; set; }
        [NotNull]
        public DateTime LastDownloadTime { get; set; }
    }
}
