using SQLite;
using System;

namespace MobileSyncModels.Model.System
{
    public class Synchronization
    {
        private string pk_;
        [PrimaryKey, MaxLength(36)]
        public string Pk { get { return pk_ ?? (pk_ = Guid.NewGuid().ToString()); } set { pk_ = value; } }
        public string Version { get; set; }
        [Ignore]
        public string Username { get; set; }
        [Ignore]
        public string Password { get; set; }
    }
}
